package com.onconavigator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A directed edge in the per-patient pathway DAG, representing a prerequisite relationship
 * between two pathway steps.
 *
 * <p>An edge {@code source -> target} means "target step requires source step to be completed
 * before it can be evaluated". This models the {@code prerequisites} array from the pathway
 * template JSONB structure as explicit relational edges.
 *
 * <p>Edges are write-once: no update operations are permitted. The {@code UNIQUE(source_step_id,
 * target_step_id)} constraint prevents duplicate edges, and {@code CHECK(source_step_id <>
 * target_step_id)} prevents self-referential edges.
 *
 * <p>Step references use UUID columns rather than {@code @ManyToOne} relationships to avoid
 * bidirectional graph navigation overhead. The pathway evaluation engine works with step IDs
 * directly when building the DAG traversal structure.
 *
 * <p>No PHI is stored in this entity. {@code @Audited} records edge creation for audit trail
 * completeness.
 */
@Entity
@Table(name = "patient_pathway_edges")
@Audited
public class PatientPathwayEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The pathway this edge belongs to. Lazy-loaded; used to scope edge queries per pathway.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pathway_id", nullable = false)
    private PatientPathway pathway;

    /**
     * UUID of the prerequisite (source) step. This step must be COMPLETED before the
     * target step is eligible for evaluation.
     */
    @Column(name = "source_step_id", nullable = false, updatable = false)
    private UUID sourceStepId;

    /**
     * UUID of the dependent (target) step. This step depends on the source step.
     */
    @Column(name = "target_step_id", nullable = false, updatable = false)
    private UUID targetStepId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

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

    public PatientPathway getPathway() {
        return pathway;
    }

    public void setPathway(PatientPathway pathway) {
        this.pathway = pathway;
    }

    public UUID getSourceStepId() {
        return sourceStepId;
    }

    public void setSourceStepId(UUID sourceStepId) {
        this.sourceStepId = sourceStepId;
    }

    public UUID getTargetStepId() {
        return targetStepId;
    }

    public void setTargetStepId(UUID targetStepId) {
        this.targetStepId = targetStepId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }
}
