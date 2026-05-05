package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for recording a new care event for a patient.
 *
 * <p>Per D-03: care events are entered manually by nurse navigators (no EMR integration in V1).
 * The {@code notes} field may contain PHI — it is encrypted at the JPA converter boundary.
 *
 * <p>The optional {@code documentId} links this care event to a previously uploaded clinical
 * document (DOC-04). When a document is uploaded and classified, the pre-filled care event
 * wizard passes this ID to associate the document with the event.
 */
public record CreateCareEventRequest(
        @NotNull(message = "Event type is required") CareEventType eventType,
        @NotNull(message = "Event date is required") LocalDate eventDate,
        @NotNull(message = "Status is required") CareEventStatus status,
        String notes,
        UUID documentId,
        // Phase 7: scheduling coordination fields (per D-07, D-10, D-13)
        LocalDate expectedCompletionDate,
        Boolean schedulingConfirmed,
        String externalFacilityName
) {}
