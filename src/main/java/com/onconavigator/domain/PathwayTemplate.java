package com.onconavigator.domain;

import com.onconavigator.domain.enums.CancerType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pathway template defining the expected care sequence for a cancer type.
 *
 * <p>Templates are configuration-as-data: adding a new cancer pathway is a data operation
 * (INSERT into pathway_templates), not a code change. The {@code templateData} JSONB column
 * stores the full step sequence with time windows, prerequisites, and alert text.
 *
 * <p>Supports template inheritance (Phase 8): a child template can reference a parent via
 * {@code parentTemplateId}. Child templates store a diff (overrides, additions, removals,
 * edge changes) in their {@code templateData} instead of a full step array. At fork time,
 * the merge engine resolves parent + child diff into a flat step list.
 *
 * <p>Multiple templates may exist per cancer type (e.g., Colorectal root + Rectal child).
 * The {@code name} and {@code description} fields provide display information.
 *
 * <p>No PHI is stored in this entity — pathway templates are clinical protocol data,
 * not patient-specific. Encryption is not applied.
 *
 * <p>{@code @Audited} creates a {@code pathway_templates_AUD} revision table to track
 * protocol version changes for compliance and reproducibility.
 */
@Entity
@Table(name = "pathway_templates")
@Audited
public class PathwayTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancer_type", columnDefinition = "cancer_type", nullable = false)
    private CancerType cancerType;

    /**
     * References the parent template for inherited (child) templates.
     * Null for root templates. Single-level inheritance only (D-03).
     */
    @Column(name = "parent_template_id")
    private UUID parentTemplateId;

    /**
     * Human-readable display name for the template (e.g., "Rectal Cancer Pathway").
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Brief clinical description of what makes this template variant different.
     * Used in the template picker UI for child templates (D-09).
     */
    @Column(name = "description")
    private String description;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /**
     * Full pathway template as JSONB.
     * Structure: array of steps, each with stepId, name, eventType, windowDays, prerequisites,
     * alertText, and suggestedAction fields.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_data", columnDefinition = "jsonb", nullable = false)
    private String templateData;

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

    public CancerType getCancerType() {
        return cancerType;
    }

    public void setCancerType(CancerType cancerType) {
        this.cancerType = cancerType;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getTemplateData() {
        return templateData;
    }

    public void setTemplateData(String templateData) {
        this.templateData = templateData;
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

    public UUID getParentTemplateId() {
        return parentTemplateId;
    }

    public void setParentTemplateId(UUID parentTemplateId) {
        this.parentTemplateId = parentTemplateId;
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
}
