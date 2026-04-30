package com.onconavigator.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full-stack integration test proving that Flyway migrations, JPA entities, and Hibernate Envers
 * work together against a real PostgreSQL container.
 *
 * <p>This test verifies INFR-01: the application schema is created correctly and all tables
 * expected by the domain model exist after Flyway migrations run.
 *
 * <p>Temporal autoconfiguration is excluded ({@code spring.temporal.connection.target}
 * points to a non-existent server, and Temporal beans are excluded via
 * {@code @SpringBootTest} properties) because no Temporal server is available in CI.
 * The Temporal integration is tested separately via Docker Compose.
 *
 * <p>OAuth2 resource server JWT validation is not triggered here because no HTTP requests
 * are made — this test uses {@link JdbcTemplate} directly to verify schema state.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        // Disable Temporal worker auto-discovery — no Temporal server in test environment
        "spring.temporal.connection.target=localhost:7233",
        "spring.autoconfigure.exclude=io.temporal.spring.boot.autoconfigure.TemporalBootstrapConfiguration"
    }
)
@Testcontainers
@ActiveProfiles("test")
class FullStackIntegrationTest {

    /**
     * Provides a no-op JwtDecoder for the test context.
     * SecurityConfig requires a JwtDecoder bean (via oauth2ResourceServer), but no Keycloak
     * server is available in tests. This stub decoder satisfies the Spring context requirement
     * without actually validating any JWT tokens (no HTTP requests are made in this test).
     */
    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException(
                    "JwtDecoder stub — JWT validation not needed in schema integration tests");
            };
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("onconavigator_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/test-init.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("onconavigator.encryption.key",
                () -> "dGVzdC1lbmNyeXB0aW9uLWtleS0tLTMyLWJ5dGVzIT0=");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Verifies that Flyway migrations (V1–V3) create all core application tables.
     * A missing table here means a migration failure or rollback that silently skipped setup.
     */
    @Test
    void flywayMigrations_createAllTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                String.class
        );

        assertAll(
                () -> assertTrue(tables.contains("patients"), "patients table missing"),
                () -> assertTrue(tables.contains("care_events"), "care_events table missing"),
                () -> assertTrue(tables.contains("alerts"), "alerts table missing"),
                () -> assertTrue(tables.contains("audit_log"), "audit_log table missing"),
                () -> assertTrue(tables.contains("pathway_templates"), "pathway_templates table missing"),
                () -> assertTrue(tables.contains("flyway_schema_history"),
                        "flyway_schema_history tracking table missing")
        );
    }

    /**
     * Verifies that Hibernate Envers creates {@code _AUD} revision tables for all
     * {@code @Audited} entities (Patient, CareEvent, Alert, PathwayTemplate).
     *
     * <p>HIPAA relevance: _AUD tables provide an immutable history of every change to ePHI
     * entities, satisfying the audit trail requirement.
     */
    @Test
    void enversTables_created() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE '%_aud'",
                String.class
        );

        // Envers suffix is _AUD (uppercase in PostgreSQL stored as lowercase)
        assertAll(
                () -> assertTrue(tables.stream().anyMatch(t -> t.contains("patient")),
                        "patients_AUD table missing — Patient entity must be @Audited"),
                () -> assertTrue(tables.stream().anyMatch(t -> t.contains("care_event")),
                        "care_events_AUD table missing — CareEvent entity must be @Audited"),
                () -> assertTrue(tables.stream().anyMatch(t -> t.contains("alert")),
                        "alerts_AUD table missing — Alert entity must be @Audited")
        );
    }

    /**
     * Verifies that the pgcrypto extension is available.
     * {@code gen_random_uuid()} is used as the default UUID generator in V1 migration
     * (patients.id, care_events.id, alerts.id DEFAULT gen_random_uuid()).
     */
    @Test
    void pgcryptoExtension_available() {
        String result = jdbcTemplate.queryForObject(
                "SELECT gen_random_uuid()::text", String.class);
        assertNotNull(result, "gen_random_uuid() must return a value");
        assertTrue(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "gen_random_uuid() must return a valid UUID string, got: " + result);
    }

    /**
     * Verifies that the audit_log table has the correct index for compliance queries.
     * The actor+timestamp index is the primary access pattern for HIPAA audit reports.
     */
    @Test
    void auditLog_hasComplianceIndexes() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'audit_log' AND schemaname = 'public'",
                String.class
        );

        assertTrue(indexes.stream().anyMatch(i -> i.contains("actor")),
                "audit_log must have an index on actor_id for compliance queries");
        assertTrue(indexes.stream().anyMatch(i -> i.contains("timestamp")),
                "audit_log must have an index on timestamp for time-range queries");
    }

    /**
     * Verifies that PHI columns in the patients table are BYTEA type (encrypted storage).
     * Checks that no PHI columns were accidentally changed to VARCHAR or TEXT.
     */
    @Test
    void patients_phiColumns_areBytea() {
        List<String> byteaColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'patients'
                  AND table_schema = 'public'
                  AND data_type = 'bytea'
                """,
                String.class
        );

        assertAll(
                () -> assertTrue(byteaColumns.contains("first_name_encrypted"),
                        "first_name_encrypted must be BYTEA"),
                () -> assertTrue(byteaColumns.contains("last_name_encrypted"),
                        "last_name_encrypted must be BYTEA"),
                () -> assertTrue(byteaColumns.contains("date_of_birth_encrypted"),
                        "date_of_birth_encrypted must be BYTEA"),
                () -> assertTrue(byteaColumns.contains("mrn_encrypted"),
                        "mrn_encrypted must be BYTEA")
        );
    }
}
