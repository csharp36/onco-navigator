package com.onconavigator.service;

import com.onconavigator.domain.AuditLogEntry;
import com.onconavigator.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service for writing HIPAA-required audit log entries.
 *
 * <p>All writes are:
 * <ul>
 *   <li><strong>Async</strong> — audit writes do not add latency to API responses</li>
 *   <li><strong>REQUIRES_NEW transaction</strong> — audit entry is committed regardless of
 *       whether the calling request transaction rolls back. A failed business operation
 *       must still be logged.</li>
 * </ul>
 *
 * <p>If an audit write fails, the failure is logged at ERROR level but never propagated
 * to the caller. Audit failures must not block patient care workflows.
 *
 * <p>PHI note: This service logs only patient UUIDs and resource types — never names,
 * DOBs, or diagnostic information. The {@code action}, {@code resourceType}, and
 * {@code resourceId} fields are operational metadata, not clinical content.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an API access event to the immutable audit log.
     *
     * <p>Called by {@link com.onconavigator.security.AuditLoggingFilter} after every
     * request to {@code /api/**}. Runs asynchronously in a new transaction to avoid
     * blocking the response and to persist regardless of the calling transaction outcome.
     *
     * @param actorId      UUID of the authenticated user; null for anonymous/unauthenticated requests
     * @param actorRole    Spring Security role of the actor (e.g., "ROLE_NURSE_NAVIGATOR")
     * @param action       HTTP method + path (e.g., "GET /api/patients")
     * @param resourceType resource category extracted from path (e.g., "patients", "alerts")
     * @param resourceId   UUID found in the request path, if any; null otherwise
     * @param ipAddress    client IP address (from X-Forwarded-For or remote addr)
     * @param success      true if HTTP response status < 400
     * @param requestPath  full request URI
     * @param httpMethod   HTTP method (GET, POST, etc.)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccess(UUID actorId, String actorRole, String action,
                          String resourceType, String resourceId,
                          String ipAddress, boolean success,
                          String requestPath, String httpMethod) {
        try {
            AuditLogEntry entry = new AuditLogEntry();
            // Use a nil UUID for anonymous actors to satisfy NOT NULL constraint
            entry.setActorId(actorId != null ? actorId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
            entry.setActorRole(actorRole != null ? actorRole : "ANONYMOUS");
            entry.setAction(truncate(action, 100));
            entry.setResourceType(truncate(resourceType != null ? resourceType : "unknown", 50));
            entry.setResourceId(resourceId);
            entry.setIpAddress(ipAddress);
            entry.setSuccess(success);
            entry.setRequestPath(truncate(requestPath, 500));
            entry.setHttpMethod(truncate(httpMethod, 10));
            entry.setTimestamp(OffsetDateTime.now());

            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit failures must not block patient care workflows.
            // Log the failure for operational alerting — this is a compliance concern.
            log.error("AUDIT_FAILURE: Failed to write audit log entry. action={}, actor={}",
                    action, actorId, e);
        }
    }

    /**
     * Records a failed authentication attempt.
     *
     * <p>HIPAA requires logging of rejected access attempts including the source IP.
     * Called when authentication fails before a user identity can be established.
     *
     * @param username  attempted username or identifier (may be empty/null if not provided)
     * @param ipAddress client IP address
     * @param reason    human-readable reason for failure (e.g., "invalid_token", "expired_token")
     */
    public void logAuthenticationFailure(String username, String ipAddress, String reason) {
        logAccess(
            null,
            "ANONYMOUS",
            "AUTH_FAILURE: " + reason,
            "authentication",
            null,
            ipAddress,
            false,
            "/auth/login",
            "POST"
        );
    }

    /**
     * Truncates a string to the specified maximum length to avoid column overflow errors.
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
