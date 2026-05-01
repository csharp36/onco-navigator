package com.onconavigator.web.dto;

import com.onconavigator.ai.model.DocumentClassification;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the document upload endpoint.
 *
 * <p>Contains the stored document ID, classification result from Claude,
 * patient match status, candidate matches (if ambiguous), and the matched patient ID
 * (if a single confident match was found).
 *
 * <p>HIPAA note: {@code classificationResult} may contain PHI (patient name, MRN, DOB)
 * extracted from the document. Transmitted over TLS to authenticated JWT holders only.
 */
public record DocumentUploadResponse(
    UUID documentId,
    DocumentClassification classificationResult,
    String patientMatchStatus,
    List<PatientCandidate> candidates,
    UUID matchedPatientId
) {
    /**
     * A candidate patient match with confidence indicator.
     */
    public record PatientCandidate(
        UUID patientId,
        String displayName,
        String mrn,
        String dateOfBirth,
        String confidence
    ) {}
}
