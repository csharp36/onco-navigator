package com.onconavigator.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for skipping a step in a patient's per-patient pathway.
 *
 * <p>A skip reason is required to document the clinical decision — this creates
 * an audit trail explaining why the step was intentionally excluded from the patient's
 * pathway. Required steps can be skipped but must have a recorded reason.
 *
 * @param reason clinical reason for skipping this step (required)
 */
public record SkipStepRequest(
        @NotBlank(message = "Skip reason is required") String reason
) {}
