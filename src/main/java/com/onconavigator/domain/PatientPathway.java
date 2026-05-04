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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-patient pathway record linking one patient to their personalised care pathway.
 *
 * <p>Each patient has exactly one pathway (enforced by {@code UNIQUE(patient_id)} in V14).
 * When a template exists, {@link #sourceTemplateId} and {@link #sourceTemplateVersion} record
 * the origin template for traceability. Pathways created by AI extraction (Phase 6) or
 * manually have {@code null} source fields.
 *
 * <p>No PHI is stored in this entity. The patient is referenced only via the JPA relationship.
 *
 * <p>{@code @Audited} creates a {@code patient_pathways_AUD} revision table via Hibernate Envers,
 * satisfying the HIPAA audit control requirement.
 */
@Entity
@Table(name = "patient_pathways")
@Audited
public class PatientPathway {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The patient this pathway belongs to. Lazy-loaded; always non-null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /**
     * UUID of the pathway template this pathway was derived from.
     * Null for pathways created by AI extraction or manual entry.
     */
    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    /**
     * Version of the source template at migration time.
     * Null when no source template.
     */
    @Column(name = "source_template_version")
    private Integer sourceTemplateVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

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

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public UUID getSourceTemplateId() {
        return sourceTemplateId;
    }

    public void setSourceTemplateId(UUID sourceTemplateId) {
        this.sourceTemplateId = sourceTemplateId;
    }

    public Integer getSourceTemplateVersion() {
        return sourceTemplateVersion;
    }

    public void setSourceTemplateVersion(Integer sourceTemplateVersion) {
        this.sourceTemplateVersion = sourceTemplateVersion;
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
}
