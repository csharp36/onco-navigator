package com.onconavigator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An immutable audit log record capturing every access or modification to ePHI.
 *
 * <p>This entity is INSERT-only. All columns are marked {@code updatable = false}
 * to prevent Hibernate from issuing UPDATE statements on existing records.
 * Database-level enforcement is provided by V3 migration (REVOKE UPDATE/DELETE/TRUNCATE
 * on audit_log from onco_app).
 *
 * <p>This entity intentionally does NOT carry {@code @Audited} — auditing the audit log
 * would create circular revision records.
 *
 * <p>HIPAA audit requirement: every PHI access attempt (including rejected ones) must be
 * logged. The {@link com.onconavigator.security.AuditLoggingFilter} writes entries here
 * before the request reaches any controller.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    /** UUID of the authenticated user who performed the action. Log this, not the username. */
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    /** Role of the actor at the time of the action (e.g., ROLE_NURSE_NAVIGATOR). */
    @Column(name = "actor_role", nullable = false, updatable = false, length = 50)
    private String actorRole;

    /** Action code (e.g., READ_PATIENT, UPDATE_CARE_EVENT, RESOLVE_ALERT). */
    @Column(name = "action", nullable = false, updatable = false, length = 100)
    private String action;

    /** Type of resource accessed (e.g., PATIENT, CARE_EVENT, ALERT). */
    @Column(name = "resource_type", nullable = false, updatable = false, length = 50)
    private String resourceType;

    /** ID of the specific resource accessed (patient UUID, care event UUID, etc.). */
    @Column(name = "resource_id", updatable = false)
    private String resourceId;

    /** UTC timestamp when the action occurred. */
    @Column(name = "timestamp", nullable = false, updatable = false)
    private OffsetDateTime timestamp;

    /** Source IP address of the request. Nullable — may be absent for internal actions. */
    @Column(name = "ip_address", updatable = false, columnDefinition = "inet")
    private String ipAddress;

    /** Whether the action was permitted and completed successfully. */
    @Column(name = "success", nullable = false, updatable = false)
    private boolean success = true;

    /**
     * SHA-256 hash of the full request/response detail for integrity verification.
     * Allows detection of log tampering without storing sensitive detail inline.
     */
    @Column(name = "detail_hash", updatable = false, length = 64)
    private String detailHash;

    /** HTTP path of the request (e.g., /api/patients/{id}). */
    @Column(name = "request_path", updatable = false, length = 500)
    private String requestPath;

    /** HTTP method of the request (e.g., GET, POST, PUT). */
    @Column(name = "http_method", updatable = false, length = 10)
    private String httpMethod;

    // ---- Getters (no setters on updatable=false fields enforces immutability in application) ----

    public Long getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getDetailHash() {
        return detailHash;
    }

    public void setDetailHash(String detailHash) {
        this.detailHash = detailHash;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }
}
