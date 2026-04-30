# Phase 2: Pathway Engine - Pattern Map

**Mapped:** 2026-04-30
**Files analyzed:** 22 new/modified files
**Analogs found:** 19 / 22

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/.../workflow/PatientPathwayWorkflow.java` | workflow-interface | event-driven | None (new tier) | no-analog |
| `src/.../workflow/PatientPathwayWorkflowImpl.java` | workflow-impl | event-driven | None (new tier) | no-analog |
| `src/.../workflow/DailySweepWorkflow.java` | workflow-interface | batch | None (new tier) | no-analog |
| `src/.../workflow/DailySweepWorkflowImpl.java` | workflow-impl | batch | None (new tier) | no-analog |
| `src/.../activity/PathwayEvaluationActivity.java` | activity-interface | request-response | None (new tier) | no-analog |
| `src/.../activity/PathwayEvaluationActivityImpl.java` | activity-impl | CRUD | `service/AuditService.java` | role-match |
| `src/.../activity/AlertGenerationActivity.java` | activity-interface | request-response | None (new tier) | no-analog |
| `src/.../activity/AlertGenerationActivityImpl.java` | activity-impl | CRUD | `service/AuditService.java` | role-match |
| `src/.../activity/SweepActivity.java` | activity-interface | batch | None (new tier) | no-analog |
| `src/.../activity/SweepActivityImpl.java` | activity-impl | CRUD | `service/AuditService.java` | role-match |
| `src/.../domain/PhysicianOverride.java` | model | CRUD | `domain/Alert.java` | exact |
| `src/.../domain/dto/PathwayStep.java` | model (record/DTO) | transform | `domain/enums/AlertType.java` | partial |
| `src/.../domain/dto/PathwayEvaluationResult.java` | model (record/DTO) | transform | None | no-analog |
| `src/.../domain/dto/CareEventSignal.java` | model (record/DTO) | event-driven | None | no-analog |
| `src/.../domain/enums/AnchorType.java` | enum | N/A | `domain/enums/AlertType.java` | exact |
| `src/.../domain/enums/DeviationType.java` | enum | N/A | `domain/enums/AlertType.java` | exact |
| `src/.../repository/PhysicianOverrideRepository.java` | repository | CRUD | `repository/AlertRepository.java` | exact |
| `src/.../repository/PathwayTemplateRepository.java` | repository | CRUD | `repository/PatientRepository.java` | exact |
| `src/.../service/PathwayService.java` | service | event-driven | `service/AuditService.java` | role-match |
| `src/.../config/TemporalConfig.java` | config | N/A | `config/AsyncConfig.java` | exact |
| `src/main/resources/db/migration/V5__*.sql` | migration (schema) | N/A | `V1__create_base_schema.sql` | exact |
| `src/main/resources/db/migration/V6__*.sql` | migration (seed data) | N/A | `V1__create_base_schema.sql` | role-match |
| `src/test/.../workflow/PatientPathwayWorkflowTest.java` | test | event-driven | `integration/FullStackIntegrationTest.java` | partial |
| `src/test/.../activity/PathwayEvaluationActivityTest.java` | test | CRUD | `repository/AuditLogRepositoryTest.java` | role-match |

## Pattern Assignments

### `src/.../domain/PhysicianOverride.java` (model, CRUD)

**Analog:** `src/main/java/com/onconavigator/domain/Alert.java`

**Imports pattern** (Alert.java lines 1-17):
```java
package com.onconavigator.domain;

import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;
```

**Entity structure pattern** (Alert.java lines 29-36):
```java
@Entity
@Table(name = "alerts")
@Audited
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
```

**UUID reference column pattern** (Alert.java lines 38-39):
```java
@Column(name = "patient_id", nullable = false)
private UUID patientId;
```

**Immutable audit fields pattern** (Alert.java lines 58-59):
```java
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;
```

**PrePersist pattern** (Alert.java lines 73-76):
```java
@PrePersist
void prePersist() {
    this.createdAt = OffsetDateTime.now();
}
```

**Key differences for PhysicianOverride:**
- No PHI fields, so no `@Convert(converter = EncryptionConverter.class)` needed
- Still needs `@Audited` (it references patient pathway data)
- Needs `@Column(updatable = false)` on all fields (overrides are immutable once created)
- No enum columns (unless `overrideType` is added)

---

### `src/.../domain/enums/AnchorType.java` (enum, N/A)

**Analog:** `src/main/java/com/onconavigator/domain/enums/AlertType.java`

**Full enum pattern** (AlertType.java lines 1-11):
```java
package com.onconavigator.domain.enums;

/**
 * Types of pathway deviations that trigger alerts.
 * Maps to the alert_type PostgreSQL enum.
 */
public enum AlertType {
    MISSING_EVENT,
    DELAYED_EVENT,
    OUT_OF_ORDER
}
```

**Apply to:** `AnchorType.java` with values `PREVIOUS_STEP, DIAGNOSIS_DATE, SPECIFIC_STEP`. Note: If `AnchorType` is only used in JSONB deserialization (not a database column), it does NOT need a PostgreSQL `CREATE TYPE` migration. It will be a Java-only enum deserialized by Jackson from the JSONB `templateData` field.

**Apply to:** `DeviationType.java` (if created separately from existing `AlertType`). Note: The existing `AlertType` enum already has `MISSING_EVENT, DELAYED_EVENT, OUT_OF_ORDER` which maps directly to the three deviation types from the spec. Evaluate whether a separate `DeviationType` is needed or if `AlertType` already covers this.

---

### `src/.../repository/PhysicianOverrideRepository.java` (repository, CRUD)

**Analog:** `src/main/java/com/onconavigator/repository/AlertRepository.java`

**Repository interface pattern** (AlertRepository.java lines 1-18):
```java
package com.onconavigator.repository;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Alert} entities.
 *
 * <p>Includes deduplication check method used by the pathway engine to avoid creating
 * duplicate OPEN alerts for the same patient and pathway step.
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {
```

**Deduplication check pattern** (AlertRepository.java lines 48-49):
```java
boolean existsByPatientIdAndPathwayStepNameAndStatus(
        UUID patientId, String pathwayStepName, AlertStatus status);
```

**Key methods for PhysicianOverrideRepository:**
- `boolean existsByPatientIdAndPathwayStepId(UUID patientId, String pathwayStepId)` -- check if override exists before generating alert
- `List<PhysicianOverride> findByPatientId(UUID patientId)` -- fetch all overrides for a patient
- `Optional<PhysicianOverride> findByPatientIdAndPathwayStepId(UUID patientId, String pathwayStepId)` -- specific override lookup

---

### `src/.../repository/PathwayTemplateRepository.java` (repository, CRUD)

**Analog:** `src/main/java/com/onconavigator/repository/PatientRepository.java`

**Repository interface pattern** (PatientRepository.java lines 1-22):
```java
package com.onconavigator.repository;

import com.onconavigator.domain.Patient;
import com.onconavigator.domain.enums.PatientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Patient} entities.
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
```

**Enum-based finder pattern** (PatientRepository.java lines 30-31):
```java
List<Patient> findByStatus(PatientStatus status);
```

**Key method for PathwayTemplateRepository:**
- `Optional<PathwayTemplate> findByCancerType(CancerType cancerType)` -- find template by cancer type (unique constraint ensures single result)

---

### `src/.../service/PathwayService.java` (service, event-driven)

**Analog:** `src/main/java/com/onconavigator/service/AuditService.java`

**Service class pattern** (AuditService.java lines 33-42):
```java
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
```

**Constructor injection pattern** (AuditService.java lines 39-42):
```java
private final AuditLogRepository auditLogRepository;

public AuditService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
}
```

**Error handling pattern** (AuditService.java lines 82-86):
```java
} catch (Exception e) {
    // Audit failures must not block patient care workflows.
    log.error("AUDIT_FAILURE: Failed to write audit log entry. action={}, actor={}",
            action, actorId, e);
}
```

**Key differences for PathwayService:**
- Injects `WorkflowClient` (Temporal) instead of a repository
- No `@Async` annotation (workflow start is synchronous)
- Methods: `startPathwayMonitoring(UUID, CancerType)`, `signalCareEventChanged(UUID, UUID)`, `deactivatePatient(UUID, String)`
- Should log only UUIDs, never PHI
- Uses SLF4J Logger like AuditService

---

### `src/.../config/TemporalConfig.java` (config, N/A)

**Analog:** `src/main/java/com/onconavigator/config/AsyncConfig.java`

**Config class pattern** (AsyncConfig.java lines 1-21):
```java
package com.onconavigator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's asynchronous method execution support.
 *
 * <p>Required for {@link com.onconavigator.service.AuditService#logAccess} to run
 * asynchronously.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    // Spring Boot's default SimpleAsyncTaskExecutor is sufficient for audit log writes.
}
```

**Key differences for TemporalConfig:**
- Defines task queue name constant(s): `public static final String TASK_QUEUE = "onco-pathway-queue";`
- Defines workflow ID prefix: `public static final String PATHWAY_WORKFLOW_ID_PREFIX = "pathway-";`
- May define namespace constant: `public static final String NAMESPACE = "default";`
- No Spring annotation beyond `@Configuration` needed (Temporal autoconfiguration handles worker setup via `application-local.yml`)

---

### `src/.../activity/PathwayEvaluationActivityImpl.java` (activity-impl, CRUD)

**Analog:** `src/main/java/com/onconavigator/service/AuditService.java` (Spring bean with constructor injection and repository access)

**Spring bean / constructor injection pattern** (AuditService.java lines 33-42):
```java
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
```

**Key differences for activity implementations:**
- Uses `@Component` instead of `@Service` (Temporal auto-discovery requires Spring component scanning)
- Adds `@ActivityImpl(workers = "onco-pathway-worker")` annotation (Temporal-specific)
- Implements the corresponding `@ActivityInterface` interface
- Has multiple repository dependencies (PatientRepository, CareEventRepository, AlertRepository, PathwayTemplateRepository, PhysicianOverrideRepository, ObjectMapper)
- All methods must be idempotent (Temporal may retry them)
- Must NOT access PHI for logging; only log patient UUIDs and step names

---

### `src/main/resources/db/migration/V5__create_physician_overrides.sql` (migration, N/A)

**Analog:** `src/main/resources/db/migration/V1__create_base_schema.sql`

**Table creation pattern** (V1 lines 51-64):
```sql
-- Alerts
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    alert_type alert_type NOT NULL,
    status alert_status NOT NULL DEFAULT 'OPEN',
    pathway_step_name VARCHAR(255) NOT NULL,
    deviation_description TEXT NOT NULL,
    suggested_action TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    resolution_notes TEXT,
    workflow_run_id VARCHAR(255)
);
```

**Index creation pattern** (V1 lines 78-84):
```sql
CREATE INDEX idx_alerts_patient_id ON alerts(patient_id);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_status_created ON alerts(status, created_at DESC);
```

**GRANT pattern for new tables** (V3 lines 20):
```sql
GRANT ALL ON patients, care_events, alerts, pathway_templates TO onco_app;
```

**Key migration content for V5:**
- CREATE TABLE `physician_overrides` with UUID PK, `patient_id` FK to `patients(id)`, `pathway_step_id` VARCHAR, `override_reason` TEXT, `created_by` UUID, `created_at` TIMESTAMP WITH TIME ZONE
- Index on `(patient_id, pathway_step_id)` -- the primary lookup pattern for override checks
- GRANT on the new table to `onco_app`
- No new PostgreSQL ENUM type needed (AnchorType lives only in JSONB, not as a DB column)

---

### `src/main/resources/db/migration/V6__seed_pathway_templates.sql` (migration, seed data)

**Analog:** `src/main/resources/db/migration/V1__create_base_schema.sql` (for SQL style and conventions)

**No direct seed migration analog exists.** This is the first seed data migration in the project. Follow V1's SQL style:
- Use `gen_random_uuid()` for IDs
- Use `NOW()` for timestamps
- Use `'00000000-0000-0000-0000-000000000000'` for system `created_by` (consistent with RESEARCH.md example)
- INSERT three rows into `pathway_templates` with JSONB `template_data` matching the D-04 structure
- Pathway step definitions must come from the V1 Feature Specification (CONTEXT.md canonical ref)

---

### `src/test/.../workflow/PatientPathwayWorkflowTest.java` (test, event-driven)

**Analog:** `src/test/java/com/onconavigator/integration/FullStackIntegrationTest.java`

**Test class structure pattern** (FullStackIntegrationTest.java lines 37-48):
```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.temporal.connection.target=localhost:7233",
        "spring.autoconfigure.exclude=io.temporal.spring.boot.autoconfigure.TemporalBootstrapConfiguration"
    }
)
@Testcontainers
@ActiveProfiles("test")
class FullStackIntegrationTest {
```

**Testcontainers PostgreSQL pattern** (FullStackIntegrationTest.java lines 66-79):
```java
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
```

**Stub JwtDecoder pattern** (FullStackIntegrationTest.java lines 55-64):
```java
@TestConfiguration
static class TestSecurityConfig {
    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException(
                "JwtDecoder stub -- JWT validation not needed in schema integration tests");
        };
    }
}
```

**Key differences for workflow tests:**
- Uses `TestWorkflowExtension` (Temporal JUnit 5) instead of `@SpringBootTest` for pure workflow logic tests
- For integration tests that need both Temporal and PostgreSQL: use `@SpringBootTest` + Testcontainers for PostgreSQL + `TestWorkflowEnvironment` for Temporal
- Time skipping via `TestWorkflowEnvironment` replaces real 24-hour waits
- Mock activities with Mockito for workflow unit tests; use real activities for integration tests
- Still exclude Temporal autoconfiguration in pure DB tests (existing pattern)

---

### `src/test/.../activity/PathwayEvaluationActivityTest.java` (test, CRUD)

**Analog:** `src/test/java/com/onconavigator/repository/AuditLogRepositoryTest.java`

**DataJpaTest pattern** (AuditLogRepositoryTest.java lines 43-47):
```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuditLogRepositoryTest {
```

**Test helper factory method pattern** (AuditLogRepositoryTest.java lines 157-169):
```java
private AuditLogEntry createTestEntry() {
    AuditLogEntry entry = new AuditLogEntry();
    entry.setActorId(UUID.randomUUID());
    entry.setActorRole("ROLE_NURSE_NAVIGATOR");
    entry.setAction("GET /api/patients");
    entry.setResourceType("patients");
    entry.setSuccess(true);
    entry.setTimestamp(OffsetDateTime.now());
    entry.setIpAddress("127.0.0.1");
    entry.setHttpMethod("GET");
    entry.setRequestPath("/api/patients");
    return entry;
}
```

**Key differences for activity tests:**
- Activity tests may need `@SpringBootTest` instead of `@DataJpaTest` if testing with Temporal's `TestWorkflowEnvironment`
- Pure activity unit tests can use plain JUnit 5 + Mockito (mock repositories, inject into activity constructor)
- Integration-level activity tests use Testcontainers for real DB and real activity logic
- Need to verify idempotency: calling `evaluate()` twice produces the same result (dedup check)

---

## Shared Patterns

### Entity Conventions
**Source:** All entities in `src/main/java/com/onconavigator/domain/`
**Apply to:** `PhysicianOverride.java`

All entities follow these conventions:
1. `@Entity` + `@Table(name = "...")` + `@Audited` (for ePHI-touching entities)
2. `@Id` + `@GeneratedValue(strategy = GenerationType.UUID)` with `UUID` type
3. `@PrePersist` sets `createdAt` (and `updatedAt` if applicable)
4. `@PreUpdate` sets `updatedAt` (if applicable)
5. `@Column(updatable = false)` on immutable fields (`createdAt`, `createdBy`)
6. PostgreSQL enum columns: `@Enumerated(EnumType.STRING)` + `@Column(columnDefinition = "pg_enum_name")`
7. PHI fields: `@Convert(converter = EncryptionConverter.class)` + `@Column(columnDefinition = "bytea")`
8. Explicit getters and setters (no Lombok)

### Enum Conventions
**Source:** `src/main/java/com/onconavigator/domain/enums/AlertType.java` (and all enums in `enums/` package)
**Apply to:** `AnchorType.java`, `DeviationType.java` (if created)

```java
package com.onconavigator.domain.enums;

/**
 * [Description].
 * Maps to the [pg_enum_name] PostgreSQL enum.
 */
public enum EnumName {
    VALUE_ONE,
    VALUE_TWO
}
```

- Javadoc states the PostgreSQL enum type name it maps to
- Only needs a PostgreSQL `CREATE TYPE` if stored as a database column (not needed for JSONB-only enums)
- SCREAMING_SNAKE_CASE values

### Repository Conventions
**Source:** `src/main/java/com/onconavigator/repository/AlertRepository.java`
**Apply to:** `PhysicianOverrideRepository.java`, `PathwayTemplateRepository.java`

```java
@Repository
public interface XxxRepository extends JpaRepository<Xxx, UUID> {
    // Spring Data derived query methods
    // Javadoc on each method explaining the query purpose
}
```

### Service Conventions
**Source:** `src/main/java/com/onconavigator/service/AuditService.java`
**Apply to:** `PathwayService.java`

1. `@Service` annotation
2. Constructor injection (no field injection)
3. `private static final Logger log = LoggerFactory.getLogger(ClassName.class);`
4. PHI-safe logging: only UUIDs, never names/DOBs/MRNs
5. Comprehensive Javadoc with `@param` tags
6. Try-catch with error logging for operations that should not propagate failures

### Flyway Migration Conventions
**Source:** `src/main/resources/db/migration/V1__create_base_schema.sql` through `V4__*.sql`
**Apply to:** `V5__create_physician_overrides.sql`, `V6__seed_pathway_templates.sql`

1. Filename: `V{N}__{description}.sql` (double underscore)
2. Comment header explaining purpose
3. `UUID PRIMARY KEY DEFAULT gen_random_uuid()` for ID columns
4. `TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()` for timestamp columns
5. Foreign keys use `REFERENCES table(id)` inline
6. Indexes created at bottom of migration
7. GRANT to `onco_app` for new tables (follows V3 pattern)

### Testcontainers / Integration Test Conventions
**Source:** `src/test/java/com/onconavigator/integration/FullStackIntegrationTest.java`
**Apply to:** All new integration tests

1. `@Testcontainers` + `@Container` for PostgreSQL 16
2. `@DynamicPropertySource` to inject container JDBC properties + encryption key
3. `withInitScript("db/test-init.sql")` for prerequisite roles/extensions
4. `@ActiveProfiles("test")` to load `application-test.yml`
5. Stub `JwtDecoder` bean via `@TestConfiguration` when SecurityConfig is in context
6. Exclude Temporal autoconfiguration via `spring.autoconfigure.exclude` property when no Temporal server is available

### PHI Safety (Cross-Cutting)
**Source:** `src/main/java/com/onconavigator/domain/Patient.java` (lines 28-34), `service/AuditService.java` (lines 28-32)
**Apply to:** All workflow, activity, and service files

```java
// From Patient.java Javadoc:
// HIPAA note: Do NOT add these fields to log statements. Log only {@link #id} (UUID).

// From AuditService.java Javadoc:
// PHI note: This service logs only patient UUIDs and resource types -- never names,
// DOBs, or diagnostic information.
```

- Temporal workflow inputs: ONLY `UUID` and `String` (for enum names like cancer type)
- Activity logging: `log.info("Evaluating pathway for patient {}", patientId)` -- never patient names
- Signal payloads: UUID only (`CareEventSignal` carries `UUID careEventId`)

---

## No Analog Found

Files with no close match in the codebase (planner should use RESEARCH.md patterns instead):

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `workflow/PatientPathwayWorkflow.java` | workflow-interface | event-driven | No Temporal workflows exist yet. Use RESEARCH.md Pattern 1 (signal+timer loop). Temporal `@WorkflowInterface` / `@WorkflowMethod` / `@SignalMethod` / `@QueryMethod` annotations are Temporal-SDK-specific with no Spring analog. |
| `workflow/PatientPathwayWorkflowImpl.java` | workflow-impl | event-driven | No Temporal workflows exist yet. Use RESEARCH.md Pattern 1. Key constraints: deterministic code only (no `System.currentTimeMillis()`, no DB access, no `new Random()`). Use `Workflow.await()`, `Workflow.currentTimeMillis()`, `Workflow.newActivityStub()`. |
| `workflow/DailySweepWorkflow.java` | workflow-interface | batch | No Temporal workflows exist yet. Use RESEARCH.md Pattern 3 (cron workflow). Simple interface with single `@WorkflowMethod void sweep()`. |
| `workflow/DailySweepWorkflowImpl.java` | workflow-impl | batch | No Temporal workflows exist yet. Use RESEARCH.md Pattern 3. Creates activity stub, calls `SweepActivity.findAndStartMissingWorkflows()`. |
| `activity/PathwayEvaluationActivity.java` | activity-interface | request-response | No Temporal activities exist yet. Temporal `@ActivityInterface` / `@ActivityMethod` annotations have no Spring analog. Interface defines `PathwayEvaluationResult evaluate(UUID patientId)` and `void closeOpenAlerts(UUID patientId)`. |
| `activity/AlertGenerationActivity.java` | activity-interface | request-response | No Temporal activities exist yet. Defines `void generateAlert(UUID patientId, String stepName, AlertType type, String description, String suggestedAction, String workflowRunId)`. |
| `activity/SweepActivity.java` | activity-interface | batch | No Temporal activities exist yet. Defines `void findAndStartMissingWorkflows()`. |
| `domain/dto/PathwayStep.java` | record/DTO | transform | First Java record in the project. Use RESEARCH.md code example for `PathwayStep` record. Consider Jackson `@JsonProperty` annotations for JSONB compatibility. |
| `domain/dto/PathwayEvaluationResult.java` | record/DTO | transform | First evaluation result DTO. Simple record with `boolean allStepsComplete` and `List<String> alertsGenerated`. |
| `domain/dto/CareEventSignal.java` | record/DTO | event-driven | Signal payload. Simple record with `UUID careEventId`. Must carry ONLY UUIDs (PHI safety). |

## YAML Configuration Pattern

**Source:** `src/main/resources/application-local.yml` (lines 24-29)

The Temporal worker auto-discovery is already configured:
```yaml
spring.temporal:
  connection:
    target: localhost:7233
  workers-auto-discovery:
    packages:
      - com.onconavigator.workflow
      - com.onconavigator.activity
```

Phase 2 needs to add worker task queue configuration. Follow the RESEARCH.md YAML example:
```yaml
spring.temporal:
  workers:
    - task-queue: onco-pathway-queue
      name: onco-pathway-worker
```

Also needs `spring.temporal` exclusion property in `application-test.yml` if not already present (it is -- handled via `@SpringBootTest` properties in existing tests).

## Metadata

**Analog search scope:** `src/main/java/com/onconavigator/` (all packages), `src/test/java/com/onconavigator/` (all test packages), `src/main/resources/db/migration/` (all Flyway migrations), `src/main/resources/` (YAML config)
**Files scanned:** 30 existing source files
**Pattern extraction date:** 2026-04-30
