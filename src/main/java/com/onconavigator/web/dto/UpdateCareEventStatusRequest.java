package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CareEventStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for updating the status of an existing care event (per DATA-03).
 *
 * <p>Nurse navigators update care event status as events progress from PENDING
 * to SCHEDULED to COMPLETED (or CANCELLED).
 */
public record UpdateCareEventStatusRequest(
        @NotNull(message = "Status is required") CareEventStatus status
) {}
