package com.onconavigator.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for document metadata listing (no blob content).
 *
 * <p>Used by the patient documents listing endpoint to return document summaries
 * without loading the full file content. The content is loaded separately via
 * the document content streaming endpoint.
 *
 * <p>HIPAA note: {@code originalFilename} may contain patient identifiers (e.g.,
 * "Smith_John_Pathology.pdf"). Transmitted over TLS to authenticated JWT holders only.
 * Never log this field.
 */
public record DocumentSummaryResponse(
        UUID id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String documentType,
        String classificationSource,
        UUID careEventId,
        OffsetDateTime createdAt
) {}
