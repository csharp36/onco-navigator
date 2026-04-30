package com.onconavigator.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a patient's complete pathway status (per ALRT-03).
 *
 * <p>Returned by the pathway status endpoint to drive the patient detail view's
 * pathway timeline visualization. Each step in the patient's cancer pathway is
 * represented as a {@link PathwayStepStatus} with current completion and alert state.
 */
public record PathwayStatusResponse(
        UUID patientId,
        List<PathwayStepStatus> steps
) {}
