package com.onconavigator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Records a physician's intentional override of a pathway step alert (D-11).
 *
 * <p>When a physician deliberately reorders or skips a standard pathway step (e.g., surgery
 * before consultation in an emergency presentation), the workflow engine must not generate
 * false-positive alerts for that patient and step. A {@code PhysicianOverride} record
 * suppresses alert generation for the specific {@code (patient_id, pathway_step_id)} pair.
 *
 * <p>No PHI is stored here. The patient is referenced only by UUID. The {@code overrideReason}
 * contains clinical process text describing the reordering rationale (e.g., "emergency
 * presentation required immediate surgical intervention"), not patient-identifying information.
 *
 * <p>{@code @Audited} creates a {@code physician_overrides_AUD} revision table, providing
 * a tamper-evident audit trail of all override creation events per HIPAA audit control
 * requirements. The entity is write-once (no update operations); the {@code UNIQUE} index
 * on {@code (patient_id, pathway_step_id)} prevents duplicate override records.
 */
@Entity
@Table(name = "physician_overrides")
@Audited
public class PhysicianOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * UUID of the patient whose pathway step is being overridden.
     * References {@code patients.id}. Never null.
     */
    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    /**
     * Pathway step identifier being overridden (e.g., "BREAST_01").
     * Matches stepId values in the pathway template JSONB.
     */
    @Column(name = "pathway_step_id", nullable = false, length = 100, updatable = false)
    private String pathwayStepId;

    /**
     * Clinical rationale for the override, authored by the authorising clinician.
     * Contains process text, not PHI.
     */
    @Column(name = "override_reason", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String overrideReason;

    /**
     * UUID of the staff member who recorded this override. Immutable after creation.
     */
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    /**
     * Timestamp when this override was created. Set automatically by {@link #prePersist()}.
     * Immutable after creation.
     */
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

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public String getPathwayStepId() {
        return pathwayStepId;
    }

    public void setPathwayStepId(String pathwayStepId) {
        this.pathwayStepId = pathwayStepId;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
