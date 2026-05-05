package com.onconavigator.domain;

import com.onconavigator.domain.enums.NotificationChannel;
import com.onconavigator.security.EncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Operational queue for notifications held during quiet hours or pending digest batching.
 *
 * <p>Items are inserted when a notification cannot be immediately dispatched (quiet hours
 * active) or when the user has digest mode enabled. The {@code DigestDispatchWorkflow}
 * periodically drains this queue, dispatching items whose {@code holdUntil} time has elapsed.
 *
 * <p>NOT {@code @Audited} -- this is an operational queue table, not a PHI audit trail.
 * The PHI audit trail is maintained by {@link NotificationLog} which records all dispatched
 * notifications. The encrypted rendered content in this table is transient (deleted or
 * marked DISPATCHED after processing).
 */
@Entity
@Table(name = "notification_pending_queue")
public class NotificationPendingQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "notification_channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "hold_type", nullable = false, length = 20)
    private String holdType;

    @Column(name = "hold_until", nullable = false)
    private OffsetDateTime holdUntil;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "rendered_content_encrypted", columnDefinition = "bytea", nullable = false)
    private String renderedContentEncrypted;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }

    // ---- Getters and setters ----

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public String getHoldType() {
        return holdType;
    }

    public void setHoldType(String holdType) {
        this.holdType = holdType;
    }

    public OffsetDateTime getHoldUntil() {
        return holdUntil;
    }

    public void setHoldUntil(OffsetDateTime holdUntil) {
        this.holdUntil = holdUntil;
    }

    public String getRenderedContentEncrypted() {
        return renderedContentEncrypted;
    }

    public void setRenderedContentEncrypted(String renderedContentEncrypted) {
        this.renderedContentEncrypted = renderedContentEncrypted;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
