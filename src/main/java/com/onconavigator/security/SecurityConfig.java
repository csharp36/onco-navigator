package com.onconavigator.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for Onco-Navigator.
 *
 * <p>Authentication: Keycloak OIDC via JWT (OAuth2 Resource Server pattern).
 * Tokens are validated against Keycloak's JWKS endpoint — no session state on
 * the server. The issuer URI is configured via
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} in the active profile.
 *
 * <p>Authorization:
 * <ul>
 *   <li>{@code /health} — public (Docker HEALTHCHECK, ECS load balancer probes)</li>
 *   <li>{@code /actuator/health} — public (Docker/ECS health checks)</li>
 *   <li>{@code /actuator/info} — public</li>
 *   <li>{@code /api/**} — requires valid JWT (any role)</li>
 *   <li>{@code /actuator/auditevents} — requires {@code ROLE_ADMIN}</li>
 *   <li>All other paths — denied (default-deny)</li>
 * </ul>
 *
 * <p>Method-level security is enabled via {@code @EnableMethodSecurity(prePostEnabled = true)},
 * allowing fine-grained {@code @PreAuthorize("hasRole('NURSE_NAVIGATOR')")} annotations
 * on service and controller methods.
 *
 * <p>HIPAA notes:
 * <ul>
 *   <li>CSRF disabled — SPA uses Bearer token, not session cookies</li>
 *   <li>Sessions STATELESS — JWT is the sole authentication mechanism</li>
 *   <li>CORS locked to Vite dev server (localhost:5173); production profile overrides this</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Primary security filter chain.
     *
     * <p>Configures stateless JWT authentication, authorization rules,
     * CORS, and disables CSRF (appropriate for token-based APIs).
     *
     * <p>The {@link AuditLoggingFilter} is added AFTER {@code BearerTokenAuthenticationFilter}
     * so the {@code SecurityContextHolder} is populated when audit data is extracted.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuditLoggingFilter auditLoggingFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // SPA uses JWT Bearer token — not cookie-based auth
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints — accessible without authentication
                .requestMatchers("/health").permitAll()         // Docker HEALTHCHECK / ECS probe
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                // All API endpoints require a valid JWT
                .requestMatchers("/api/**").authenticated()
                // Audit events endpoint restricted to administrators
                .requestMatchers("/actuator/auditevents").hasRole("ADMIN")
                // Default deny — block anything else
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            // Register AuditLoggingFilter inside the Spring Security chain so that
            // SecurityContextHolder is still populated when audit data is read.
            .addFilterAfter(auditLoggingFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Prevents Spring Boot from auto-registering {@link AuditLoggingFilter} as a
     * standalone servlet filter. The filter is registered inside the Spring Security
     * chain via {@code http.addFilterAfter()} and must not be registered twice.
     */
    @Bean
    public FilterRegistrationBean<AuditLoggingFilter> auditLoggingFilterRegistration(
            AuditLoggingFilter auditLoggingFilter) {
        FilterRegistrationBean<AuditLoggingFilter> registration =
            new FilterRegistrationBean<>(auditLoggingFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * JWT authentication converter that maps Keycloak realm roles to Spring authorities.
     *
     * <p>Delegates role extraction to {@link KeycloakJwtRoleConverter}, which reads
     * {@code realm_access.roles} from the JWT payload and maps {@code ROLE_*} values.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakJwtRoleConverter());
        return converter;
    }

    /**
     * CORS configuration allowing the Vite development server to call the API.
     *
     * <p>In production, this origin list must be overridden via the {@code aws} profile
     * to allow only the actual application domain.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173")); // Vite dev server
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID"));
        config.setExposedHeaders(List.of("X-Request-ID"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
