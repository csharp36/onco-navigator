package com.onconavigator.domain;

import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.domain.enums.PathwayStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single step in a per-patient care pathway DAG.
 *
 * <p>Steps are derived from pathway templates at patient enrollment time (V15 migration) or
 * proposed by the AI document ingestion pipeline (Phase 6). Each step tracks its evaluation
 * status via {@link PathwayStepStatus} and links back to its template origin via
 * {@link #sourceTemplateStepId} for traceability.
 *
 * <p>The {@link #version} field provides optimistic locking: concurrent status updates
 * (e.g., evaluation engine marking COMPLETED while nurse marks SKIPPED) are serialised
 * safely without row-level locks.
 *
 * <p>No PHI is stored in this entity. Step names and descriptions are clinical process
 * data (e.g., "Surgery", "Pathology Report"), not patient-identifying information.
 *
 * <p>{@code @Audited} creates a {@code patient_pathway_steps_AUD} revision table via Hibernate
 * Envers, satisfying the HIPAA audit control requirement for all status changes.
 */
@Entity
@Table(name = "patient_pathway_steps")
@Audited
public class PatientPathwayStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The pathway this step belongs to. Lazy-loaded; always non-null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pathway_id", nullable = false)
    private PatientPathway pathway;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Care event type this step monitors. Uses the existing care_event_type PostgreSQL enum.
     * Nullable — AI-proposed steps may have no event type until confirmed.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", columnDefinition = "care_event_type")
    private CareEventType eventType;

    /**
     * Expected number of days within which this step should complete after its anchor.
     */
    @Column(name = "window_days")
    private Integer windowDays;

    /**
     * Whether this step is required for pathway completion. Defaults to true.
     */
    @Column(name = "required", nullable = false)
    private boolean required = true;

    /**
     * Current evaluation status. Uses the pathway_step_status PostgreSQL enum.
     * Defaults to ACTIVE at creation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "pathway_step_status", nullable = false)
    private PathwayStepStatus status = PathwayStepStatus.ACTIVE;

    /**
     * Reason recorded when a step is SKIPPED. Contains clinical process text, not PHI.
     */
    @Column(name = "skip_reason", columnDefinition = "TEXT")
    private String skipReason;

    /**
     * Alert text shown to nurse navigators when this step is overdue or missing.
     */
    @Column(name = "alert_text", columnDefinition = "TEXT")
    private String alertText;

    /**
     * Suggested corrective action for nurse navigators when the step triggers an alert.
     */
    @Column(name = "suggested_action", columnDefinition = "TEXT")
    private String suggestedAction;

    /**
     * Step identifier from the source pathway template (e.g., "BREAST_01").
     * Used for override matching and migration traceability. Null for AI-proposed steps.
     */
    @Column(name = "source_template_step_id", length = 100)
    private String sourceTemplateStepId;

    // Phase 6: AI extraction source tracking

    /**
     * Origin of this step: 'TEMPLATE', 'MANUAL', or 'AI_EXTRACTED'.
     * Null for steps created before Phase 6.
     */
    @Column(name = "source", length = 50)
    private String source;

    /**
     * FK to clinical_documents.id when source is 'AI_EXTRACTED'.
     * Provides traceability from the proposed step back to the document that triggered extraction.
     * Nullable — null for TEMPLATE and MANUAL steps.
     */
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    /**
     * JSON representation of proposed DAG edges for this step (D-12).
     * Populated by AI extraction; consumed by PatientPathwayService to create
     * patient_pathway_edges after nurse confirmation.
     * Nullable — null for non-AI steps and after edges are persisted.
     */
    @Column(name = "proposed_edges_json", columnDefinition = "TEXT")
    private String proposedEdgesJson;

    /**
     * Timestamp when this step was completed. Set when status transitions to COMPLETED.
     */
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /**
     * UUID of the care event that completed this step. Provides explicit linkage from
     * the step to the care event that satisfied it (per RESEARCH open question 1).
     */
    @Column(name = "completed_care_event_id")
    private UUID completedCareEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    /**
     * Optimistic locking version. Prevents lost updates when concurrent transactions
     * update step status simultaneously.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

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

    public PatientPathway getPathway() {
        return pathway;
    }

    public void setPathway(PatientPathway pathway) {
        this.pathway = pathway;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CareEventType getEventType() {
        return eventType;
    }

    public void setEventType(CareEventType eventType) {
        this.eventType = eventType;
    }

    public Integer getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public PathwayStepStatus getStatus() {
        return status;
    }

    public void setStatus(PathwayStepStatus status) {
        this.status = status;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public String getAlertText() {
        return alertText;
    }

    public void setAlertText(String alertText) {
        this.alertText = alertText;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
    }

    public String getSourceTemplateStepId() {
        return sourceTemplateStepId;
    }

    public void setSourceTemplateStepId(String sourceTemplateStepId) {
        this.sourceTemplateStepId = sourceTemplateStepId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(UUID sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }

    public String getProposedEdgesJson() {
        return proposedEdgesJson;
    }

    public void setProposedEdgesJson(String proposedEdgesJson) {
        this.proposedEdgesJson = proposedEdgesJson;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public UUID getCompletedCareEventId() {
        return completedCareEventId;
    }

    public void setCompletedCareEventId(UUID completedCareEventId) {
        this.completedCareEventId = completedCareEventId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
