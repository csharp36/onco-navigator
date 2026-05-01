package com.onconavigator.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured output record for Claude document classification.
 *
 * <p>Spring AI's {@code ChatClient.entity(DocumentClassification.class)} generates a JSON Schema
 * from this record and instructs Claude to respond with matching JSON. Fields marked
 * {@code required = true} must always be present in the response; optional fields may be null
 * when not determinable from the document text.
 *
 * <p>HIPAA note: This record may contain PHI (patientName, mrn, dateOfBirth, extractedNotes).
 * Never log instances of this record. Pass only through encrypted channels.
 */
@JsonPropertyOrder({"documentType", "confidence", "mrn", "patientName",
                     "dateOfBirth", "eventType", "eventDate", "extractedNotes"})
public record DocumentClassification(
    @JsonProperty(required = true, value = "documentType") String documentType,
    @JsonProperty(required = true, value = "confidence") String confidence,
    @JsonProperty(value = "mrn") String mrn,
    @JsonProperty(value = "patientName") String patientName,
    @JsonProperty(value = "dateOfBirth") String dateOfBirth,
    @JsonProperty(value = "eventType") String eventType,
    @JsonProperty(value = "eventDate") String eventDate,
    @JsonProperty(value = "extractedNotes") String extractedNotes
) {}
