package com.onconavigator.domain;

import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.domain.enums.PatientStatus;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Patient enrolled in pathway monitoring.
 *
 * <p>All PHI fields (firstName, lastName, dateOfBirth, mrn) are encrypted at the JPA
 * converter boundary using AES-256-GCM before JDBC writes them to the database.
 * The corresponding database columns are {@code BYTEA} — no readable PHI touches PostgreSQL.
 *
 * <p>{@code @Audited} creates a {@code patients_AUD} revision table via Hibernate Envers,
 * satisfying the HIPAA audit control requirement for all ePHI-touching entities.
 *
 * <p>HIPAA note: Do NOT add these fields to log statements. Log only {@link #id} (UUID).
 */
@Entity
@Table(name = "patients")
@Audited
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "first_name_encrypted", columnDefinition = "bytea", nullable = false)
    private String firstName;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "last_name_encrypted", columnDefinition = "bytea", nullable = false)
    private String lastName;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "date_of_birth_encrypted", columnDefinition = "bytea", nullable = false)
    private String dateOfBirth;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "mrn_encrypted", columnDefinition = "bytea", nullable = false)
    private String mrn;

    @Column(name = "mrn_hmac_token", length = 64)
    private String mrnHmacToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancer_type", columnDefinition = "cancer_type", nullable = false)
    private CancerType cancerType;

    @Column(name = "cancer_stage", nullable = false)
    private String cancerStage;

    @Column(name = "diagnosis_date", nullable = false)
    private LocalDate diagnosisDate;

    @Column(name = "assigned_navigator_id")
    private UUID assignedNavigatorId;

    @Column(name = "treating_physician")
    private String treatingPhysician;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "patient_status", nullable = false)
    private PatientStatus status = PatientStatus.ACTIVE;

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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getMrn() {
        return mrn;
    }

    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    public String getMrnHmacToken() {
        return mrnHmacToken;
    }

    public void setMrnHmacToken(String mrnHmacToken) {
        this.mrnHmacToken = mrnHmacToken;
    }

    public CancerType getCancerType() {
        return cancerType;
    }

    public void setCancerType(CancerType cancerType) {
        this.cancerType = cancerType;
    }

    public String getCancerStage() {
        return cancerStage;
    }

    public void setCancerStage(String cancerStage) {
        this.cancerStage = cancerStage;
    }

    public LocalDate getDiagnosisDate() {
        return diagnosisDate;
    }

    public void setDiagnosisDate(LocalDate diagnosisDate) {
        this.diagnosisDate = diagnosisDate;
    }

    public UUID getAssignedNavigatorId() {
        return assignedNavigatorId;
    }

    public void setAssignedNavigatorId(UUID assignedNavigatorId) {
        this.assignedNavigatorId = assignedNavigatorId;
    }

    public String getTreatingPhysician() {
        return treatingPhysician;
    }

    public void setTreatingPhysician(String treatingPhysician) {
        this.treatingPhysician = treatingPhysician;
    }

    public PatientStatus getStatus() {
        return status;
    }

    public void setStatus(PatientStatus status) {
        this.status = status;
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
