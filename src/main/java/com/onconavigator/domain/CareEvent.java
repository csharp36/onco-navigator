package com.onconavigator.domain;

import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.security.EncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A single care event recorded for a patient along their oncology pathway.
 *
 * <p>The {@code notes} field may contain clinical notes with PHI, so it is
 * encrypted at rest via {@link EncryptionConverter}.
 *
 * <p>{@code @Audited} creates a {@code care_events_AUD} revision table via Hibernate Envers,
 * satisfying HIPAA audit requirements for ePHI-touching entities.
 */
@Entity
@Table(name = "care_events")
@Audited
public class CareEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", columnDefinition = "care_event_type", nullable = false)
    private CareEventType eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "care_event_status", nullable = false)
    private CareEventStatus status = CareEventStatus.PENDING;

    /**
     * Clinical notes associated with this care event.
     * Encrypted at rest — notes may contain PHI (HIPAA requirement).
     */
    @Convert(converter = EncryptionConverter.class)
    @Column(name = "notes_encrypted", columnDefinition = "bytea")
    private String notes;

    @Column(name = "pathway_step_id")
    private String pathwayStepId;

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

    public CareEventType getEventType() {
        return eventType;
    }

    public void setEventType(CareEventType eventType) {
        this.eventType = eventType;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public CareEventStatus getStatus() {
        return status;
    }

    public void setStatus(CareEventStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPathwayStepId() {
        return pathwayStepId;
    }

    public void setPathwayStepId(String pathwayStepId) {
        this.pathwayStepId = pathwayStepId;
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
