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
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable audit trail record for notification dispatch.
 *
 * <p>Each row records a single notification sent (or attempted) to a user via a
 * specific channel. The rendered content contains PHI (patient name, MRN embedded
 * in the notification text) and is encrypted at rest via {@link EncryptionConverter}.
 *
 * <p>{@code @Audited} creates a {@code notification_log_AUD} revision table via
 * Hibernate Envers, satisfying the HIPAA audit control requirement for PHI-containing
 * entities (D-10).
 *
 * <p>Most fields are {@code updatable = false} because notification log entries are
 * write-once by design. Only {@code status} may be updated (e.g., from SENT to FAILED).
 * JPA requires setters for initial entity construction even on updatable=false fields.
 */
@Entity
@Table(name = "notification_log")
@Audited
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "notification_channel", nullable = false,
            updatable = false)
    private NotificationChannel channel;

    // PHI: rendered_content contains patient name + MRN embedded in notification text
    @Convert(converter = EncryptionConverter.class)
    @Column(name = "rendered_content", columnDefinition = "bytea", nullable = false,
            updatable = false)
    private String renderedContent;

    @Column(name = "is_digest", nullable = false, updatable = false)
    private boolean digest = false;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SENT";

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    @PrePersist
    void prePersist() {
        this.sentAt = OffsetDateTime.now();
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

    public String getRenderedContent() {
        return renderedContent;
    }

    public void setRenderedContent(String renderedContent) {
        this.renderedContent = renderedContent;
    }

    public boolean isDigest() {
        return digest;
    }

    public void setDigest(boolean digest) {
        this.digest = digest;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
