package com.onconavigator.domain;

import com.onconavigator.security.EncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * A clinical document (PDF, image) uploaded and linked to a patient and optionally a care event.
 *
 * <p>The {@code content} field stores the raw file bytes as a PostgreSQL BYTEA column.
 * Storage-level encryption (AWS RDS KMS) protects this at rest. The JPA EncryptionConverter
 * is NOT applied to content because it operates on String, not byte[].
 *
 * <p>The {@code extractedText} field contains OCR/PDFBox extracted text which may contain PHI,
 * so it is encrypted at the column level via {@link EncryptionConverter} (AES-256-GCM).
 *
 * <p>{@code @Audited} creates a {@code clinical_documents_AUD} revision table via Hibernate Envers,
 * satisfying HIPAA audit requirements for ePHI-touching entities.
 *
 * <p>HIPAA note: Do NOT log extractedText, originalFilename (may contain patient name),
 * or content. Log only {@link #id} (UUID).
 */
@Entity
@Table(name = "clinical_documents")
@Audited
public class ClinicalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "care_event_id")
    private UUID careEventId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    /**
     * Classified document type (e.g., PATHOLOGY_REPORT, RADIOLOGY_REPORT).
     * Nullable when classification fails or has not been attempted.
     */
    @Column(name = "document_type")
    private String documentType;

    /**
     * Source of classification: "claude", "manual", or "unclassified".
     */
    @Column(name = "classification_source")
    private String classificationSource;

    /**
     * Raw file content stored as PostgreSQL BYTEA.
     * Do NOT use @Lob — explicit columnDefinition = "bytea" avoids Hibernate OID mapping issues.
     * Do NOT apply EncryptionConverter — converter works on String, not byte[].
     */
    @Column(name = "content", columnDefinition = "bytea", nullable = false)
    private byte[] content;

    /**
     * Text extracted from the document via PDFBox or Tesseract OCR.
     * May contain PHI — encrypted at rest via EncryptionConverter (AES-256-GCM).
     * The database column is extracted_text_encrypted (BYTEA).
     */
    @Convert(converter = EncryptionConverter.class)
    @Column(name = "extracted_text_encrypted", columnDefinition = "bytea")
    private String extractedText;

    /**
     * Tesseract OCR mean confidence score (0-100).
     * Null for documents processed via PDFBox text extraction path.
     */
    @Column(name = "extraction_confidence")
    private Integer extractionConfidence;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

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

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public UUID getCareEventId() {
        return careEventId;
    }

    public void setCareEventId(UUID careEventId) {
        this.careEventId = careEventId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getClassificationSource() {
        return classificationSource;
    }

    public void setClassificationSource(String classificationSource) {
        this.classificationSource = classificationSource;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public Integer getExtractionConfidence() {
        return extractionConfidence;
    }

    public void setExtractionConfidence(Integer extractionConfidence) {
        this.extractionConfidence = extractionConfidence;
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
