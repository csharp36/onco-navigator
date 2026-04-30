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

/**
 * A deviation alert surfaced by the pathway monitoring engine.
 *
 * <p>Alerts do not contain raw PHI — they reference patients by UUID and describe
 * deviations in clinical process terms (e.g., "Consultation not recorded within 14 days
 * of referral"). Text fields contain non-PHI clinical guidance, not patient data.
 *
 * <p>{@code @Audited} creates an {@code alerts_AUD} revision table, providing an audit
 * trail of alert lifecycle changes (acknowledgment, resolution, resolution notes).
 */
@Entity
@Table(name = "alerts")
@Audited
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", columnDefinition = "alert_type", nullable = false)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "alert_status", nullable = false)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "pathway_step_name", nullable = false)
    private String pathwayStepName;

    @Column(name = "deviation_description", nullable = false, columnDefinition = "TEXT")
    private String deviationDescription;

    @Column(name = "suggested_action", columnDefinition = "TEXT")
    private String suggestedAction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "workflow_run_id")
    private String workflowRunId;

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

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public String getPathwayStepName() {
        return pathwayStepName;
    }

    public void setPathwayStepName(String pathwayStepName) {
        this.pathwayStepName = pathwayStepName;
    }

    public String getDeviationDescription() {
        return deviationDescription;
    }

    public void setDeviationDescription(String deviationDescription) {
        this.deviationDescription = deviationDescription;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(UUID resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public String getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(String workflowRunId) {
        this.workflowRunId = workflowRunId;
    }
}
