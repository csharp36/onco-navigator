package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request body for recording a new care event for a patient.
 *
 * <p>Per D-03: care events are entered manually by nurse navigators (no EMR integration in V1).
 * The {@code notes} field may contain PHI — it is encrypted at the JPA converter boundary.
 */
public record CreateCareEventRequest(
        @NotNull(message = "Event type is required") CareEventType eventType,
        @NotNull(message = "Event date is required") LocalDate eventDate,
        @NotNull(message = "Status is required") CareEventStatus status,
        String notes
) {}
