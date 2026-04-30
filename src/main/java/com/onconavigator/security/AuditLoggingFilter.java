package com.onconavigator.security;

import com.onconavigator.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that writes a HIPAA-required audit log entry for every API request.
 *
 * <p>This filter runs after the Spring Security filter chain has processed authentication,
 * so the {@link SecurityContextHolder} context is populated when audit data is extracted.
 * It extends {@link OncePerRequestFilter} to guarantee exactly one audit entry per request,
 * even in forward/include dispatch scenarios.
 *
 * <p>The filter is excluded from health-check and info endpoints to avoid flooding
 * the audit log with operational noise from load balancer probes.
 *
 * <p>HIPAA requirements satisfied:
 * <ul>
 *   <li>Access logging: every API call is recorded with actor, resource, and outcome</li>
 *   <li>Authentication failure logging: unauthenticated requests produce audit entries</li>
 *   <li>IP address capture: client IP extracted from X-Forwarded-For or remote address</li>
 * </ul>
 *
 * <p>The actual write is delegated to {@link AuditService}, which executes asynchronously
 * in a separate transaction — audit logging never blocks or fails the request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditLoggingFilter extends OncePerRequestFilter {

    private final AuditService auditService;

    public AuditLoggingFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Execute the full filter chain first to get the final response status.
        // Security processing happens in the chain — authentication context is available
        // after this call returns.
        filterChain.doFilter(request, response);

        // Extract audit data from the completed request context
        UUID actorId = extractActorId();
        String actorRole = extractActorRole();
        String action = determineAction(request);
        String resourceType = extractResourceType(request);
        String resourceId = extractResourceId(request);
        String ipAddress = getClientIp(request);
        boolean success = response.getStatus() < 400;

        auditService.logAccess(
            actorId,
            actorRole,
            action,
            resourceType,
            resourceId,
            ipAddress,
            success,
            request.getRequestURI(),
            request.getMethod()
        );
    }

    /**
     * Excludes health check, info, and static resource paths from audit logging.
     * Load balancer probes should not generate audit log noise.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
            || path.startsWith("/actuator/info")
            || path.equals("/favicon.ico");
    }

    /**
     * Extracts the authenticated user's UUID from the JWT {@code sub} claim.
     * Returns null for unauthenticated requests.
     */
    private UUID extractActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            try {
                return subject != null ? UUID.fromString(subject) : null;
            } catch (IllegalArgumentException e) {
                // Subject is not a UUID (non-standard Keycloak config) — return null
                return null;
            }
        }
        return null;
    }

    /**
     * Returns the first {@code ROLE_*} authority from the security context,
     * or "ANONYMOUS" if no authentication is present.
     */
    private String extractActorRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .orElse("UNKNOWN");
        }
        return "ANONYMOUS";
    }

    /**
     * Builds an action string combining HTTP method and request URI.
     * Example: "GET /api/patients"
     */
    private String determineAction(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    /**
     * Extracts the resource type from the path segment immediately following "/api/".
     * Example: "/api/patients/uuid" -> "patients"
     */
    private String extractResourceType(HttpServletRequest request) {
        String path = request.getRequestURI();
        String[] segments = path.split("/");
        // Find segment after "api"
        for (int i = 0; i < segments.length - 1; i++) {
            if ("api".equals(segments[i]) && i + 1 < segments.length) {
                return segments[i + 1];
            }
        }
        return "unknown";
    }

    /**
     * Extracts a UUID resource ID from the request path, if present.
     * Example: "/api/patients/550e8400-e29b-41d4-a716-446655440000" -> the UUID string
     */
    private String extractResourceId(HttpServletRequest request) {
        String path = request.getRequestURI();
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                return segment;
            }
        }
        return null;
    }

    /**
     * Returns the real client IP address, preferring the X-Forwarded-For header
     * (set by load balancers) over the direct remote address.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
