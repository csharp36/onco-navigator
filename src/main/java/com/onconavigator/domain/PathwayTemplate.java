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
    @Column(name = "cancer_type", columnDefinition = "cancer_type", nullable = false, unique = true)
    private CancerType cancerType;

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
}
