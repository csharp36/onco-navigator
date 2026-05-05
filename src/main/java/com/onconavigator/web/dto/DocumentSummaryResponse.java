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
 * <p>{@code alreadyCoveredEventTypes} is a comma-separated list of CareEventType values
 * that Claude identified in the source document but were already tracked in the patient's
 * pathway. Populated by StepExtractionTriggerService (Phase 6). Null for documents
 * processed before Phase 6 or without step extraction. Used by the D-10 frontend
 * transparency display via the {@code useDocumentAlreadyCovered} hook.
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
        OffsetDateTime createdAt,
        String alreadyCoveredEventTypes  // Phase 6 D-10: comma-separated CareEventType values from extraction
) {}
