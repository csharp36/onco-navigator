package com.onconavigator.security;

import com.onconavigator.repository.ClinicalDocumentRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.service.AlertService;
import com.onconavigator.service.AuditService;
import com.onconavigator.service.DocumentProcessingService;
import com.onconavigator.service.PathwayStatusService;
import com.onconavigator.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link AuditLoggingFilter} verifying that audit entries are written
 * for API requests and omitted for excluded paths.
 *
 * <p>Uses Spring Security Test's {@code jwt()} post-processor to simulate authenticated
 * and unauthenticated scenarios without a running Keycloak instance.
 *
 * <p>The {@link AuditService} is mocked to verify that
 * {@link AuditService#logAccess} is called with the expected parameters.
 */
@WebMvcTest
@Import({SecurityConfig.class, AuditLoggingFilter.class})
class AuditLoggingFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    // Mock beans for all controllers loaded by @WebMvcTest
    @MockitoBean
    private AlertService alertService;
    @MockitoBean
    private PatientService patientService;
    @MockitoBean
    private PathwayStatusService pathwayStatusService;
    @MockitoBean
    private DocumentProcessingService documentProcessingService;
    @MockitoBean
    private ClinicalDocumentRepository clinicalDocumentRepository;
    @MockitoBean
    private PatientRepository patientRepository;

    /**
     * Satisfies Spring Security's oauth2ResourceServer() requirement.
     * The jwt() post-processor from spring-security-test bypasses actual JWT validation,
     * but the JwtDecoder bean must exist for the SecurityFilterChain to be created.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /**
     * Authenticated API requests must generate an audit entry capturing actor UUID,
     * role, action (method + path), resource type, and success status.
     */
    @Test
    void apiRequest_authenticated_generatesAuditEntry() throws Exception {
        String actorSubject = "550e8400-e29b-41d4-a716-446655440000";

        mockMvc.perform(get("/api/patients")
            .with(jwt()
                .jwt(builder -> builder.subject(actorSubject))
                .authorities(new SimpleGrantedAuthority("ROLE_NURSE_NAVIGATOR"))))
            // 200 = security passed and controller handled the request (mock returns empty)
            .andExpect(status().isOk());

        verify(auditService).logAccess(
            eq(UUID.fromString(actorSubject)),
            eq("ROLE_NURSE_NAVIGATOR"),
            contains("GET"),
            anyString(),
            isNull(),
            anyString(),
            anyBoolean(),
            eq("/api/patients"),
            eq("GET")
        );
    }

    /**
     * Unauthenticated requests to protected API endpoints must also generate audit entries.
     * HIPAA requires logging of rejected access attempts, not just successful ones.
     */
    @Test
    void apiRequest_unauthenticated_generatesAuditEntryWithAnonymousActor() throws Exception {
        mockMvc.perform(get("/api/patients"))
            .andExpect(status().isUnauthorized());

        verify(auditService).logAccess(
            isNull(),
            eq("ANONYMOUS"),
            anyString(),
            anyString(),
            isNull(),
            anyString(),
            eq(false),
            eq("/api/patients"),
            eq("GET")
        );
    }

    /**
     * Health endpoint is excluded from audit logging via {@code shouldNotFilter}.
     * Load balancer probes should not flood the audit log.
     *
     * <p>In the {@code @WebMvcTest} slice, the actuator endpoint is not registered so the
     * response is 404. The important assertion is that {@link AuditService#logAccess} is
     * never called — {@code shouldNotFilter()} excluded the path before the filter body ran.
     */
    @Test
    void healthEndpoint_notAudited() throws Exception {
        // No actuator handler in WebMvcTest slice — response may be 404 or 500.
        // The key assertion: logAccess is never called because shouldNotFilter() returned true.
        mockMvc.perform(get("/actuator/health"));

        verify(auditService, never()).logAccess(
            any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }
}
