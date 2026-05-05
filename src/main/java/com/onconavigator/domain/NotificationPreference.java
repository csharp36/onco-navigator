package com.onconavigator.domain;

import com.onconavigator.domain.enums.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Notification preference configuration for external alert dispatch channels.
 *
 * <p>Supports two models: admin defaults (user_id = NULL, is_admin_default = TRUE)
 * and user-specific overrides (user_id set). Per D-12, admin sets org-wide defaults;
 * users can override their own channel preferences.
 *
 * <p>NOT {@code @Audited} -- this entity contains no ePHI. It stores preference
 * metadata only (channel selection, severity filters, quiet hours, digest settings).
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "is_admin_default", nullable = false)
    private boolean adminDefault = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "notification_channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "alert_type_filter", columnDefinition = "text[]")
    private String[] alertTypeFilter = new String[0];

    @Column(name = "quiet_hours_start")
    private Integer quietHoursStart;

    @Column(name = "quiet_hours_end")
    private Integer quietHoursEnd;

    @Column(name = "timezone", length = 100, nullable = false)
    private String timezone = "UTC";

    @Column(name = "digest_enabled", nullable = false)
    private boolean digestEnabled = false;

    @Column(name = "digest_interval_hours", nullable = false)
    private int digestIntervalHours = 4;

    @Column(name = "next_digest_at")
    private OffsetDateTime nextDigestAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ---- Getters and setters ----

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public boolean isAdminDefault() {
        return adminDefault;
    }

    public void setAdminDefault(boolean adminDefault) {
        this.adminDefault = adminDefault;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String[] getAlertTypeFilter() {
        return alertTypeFilter;
    }

    public void setAlertTypeFilter(String[] alertTypeFilter) {
        this.alertTypeFilter = alertTypeFilter;
    }

    public Integer getQuietHoursStart() {
        return quietHoursStart;
    }

    public void setQuietHoursStart(Integer quietHoursStart) {
        this.quietHoursStart = quietHoursStart;
    }

    public Integer getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHoursEnd(Integer quietHoursEnd) {
        this.quietHoursEnd = quietHoursEnd;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isDigestEnabled() {
        return digestEnabled;
    }

    public void setDigestEnabled(boolean digestEnabled) {
        this.digestEnabled = digestEnabled;
    }

    public int getDigestIntervalHours() {
        return digestIntervalHours;
    }

    public void setDigestIntervalHours(int digestIntervalHours) {
        this.digestIntervalHours = digestIntervalHours;
    }

    public OffsetDateTime getNextDigestAt() {
        return nextDigestAt;
    }

    public void setNextDigestAt(OffsetDateTime nextDigestAt) {
        this.nextDigestAt = nextDigestAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
