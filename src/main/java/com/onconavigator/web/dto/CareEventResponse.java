package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a care event record.
 *
 * <p>The {@code notes} field is decrypted server-side from its {@code BYTEA} column.
 * Transmitted over TLS to authenticated JWT holders only.
 *
 * <p>HIPAA note: {@code notes} may contain PHI. Never log this field on the backend.
 */
public record CareEventResponse(
        UUID id,
        UUID patientId,
        CareEventType eventType,
        LocalDate eventDate,
        CareEventStatus status,
        String notes,
        String pathwayStepId,
        OffsetDateTime createdAt,
        // Phase 7: scheduling coordination fields
        LocalDate expectedCompletionDate,
        boolean schedulingConfirmed,
        String externalFacilityName
) {}
