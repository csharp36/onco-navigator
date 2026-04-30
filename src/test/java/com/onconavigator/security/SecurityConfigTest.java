package com.onconavigator.security;

import com.onconavigator.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link SecurityConfig} verifying the authorization rules.
 *
 * <p>Uses {@code @WebMvcTest} with the security config imported to test the filter chain
 * in isolation. No running Keycloak instance is needed — Spring Security Test's
 * {@code jwt()} request post-processor mocks JWT validation.
 *
 * <p>Key assertions:
 * <ul>
 *   <li>Unauthenticated requests to /api/** are rejected with 401</li>
 *   <li>Authenticated requests pass through the security filter (may 404 if no controller)</li>
 *   <li>/actuator/health is publicly accessible without any token</li>
 * </ul>
 */
@WebMvcTest
@Import({SecurityConfig.class, AuditLoggingFilter.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    /**
     * Satisfies Spring Security's oauth2ResourceServer() requirement.
     * The jwt() post-processor from spring-security-test bypasses actual JWT validation,
     * but the JwtDecoder bean must exist for the SecurityFilterChain to be created.
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /**
     * Health endpoint must be accessible without authentication.
     * Used by Docker Compose and ECS health checks that do not carry JWT tokens.
     *
     * <p>In the {@code @WebMvcTest} slice, the actuator endpoint is not registered (no
     * management context), so the response is 404 (no handler). 404 confirms the security
     * filter chain permitted the request through — the security rule is correct.
     * In production, {@code /actuator/health} is registered by Actuator auto-configuration
     * and returns 200.
     */
    @Test
    void healthEndpoint_noAuth_permittedBySecurityFilter() throws Exception {
        // 404 = security passed (path not blocked) but no handler registered in WebMvcTest slice.
        // Confirms the permitAll() rule is in effect — a 401 or 403 would mean the rule is missing.
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isNotFound());
    }

    /**
     * API endpoints must reject unauthenticated requests with 401 Unauthorized.
     * This is the primary HIPAA access control requirement.
     */
    @Test
    void apiEndpoint_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/patients"))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Authenticated requests to API endpoints should pass through the security filter.
     * Without an actual controller, the response is 404 (no mapping) — not 401/403.
     * This verifies the security filter allows authenticated requests through.
     */
    @Test
    void apiEndpoint_withValidJwt_passesSecurityFilter() throws Exception {
        mockMvc.perform(get("/api/patients")
            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_NURSE_NAVIGATOR"))))
            // 404 means security passed — no controller is registered in this test context,
            // but the request was not rejected by the security filter
            .andExpect(status().isNotFound());
    }
}
