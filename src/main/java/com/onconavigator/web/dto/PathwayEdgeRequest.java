package com.onconavigator.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for creating a prerequisite edge in a patient's pathway DAG.
 *
 * <p>An edge {@code sourceStepId -> targetStepId} means the target step requires
 * the source step to be COMPLETED before evaluation. The service layer validates
 * that both steps belong to the same patient's pathway and that adding this edge
 * would not create a cycle (D-09).
 *
 * @param sourceStepId UUID of the prerequisite (source) step
 * @param targetStepId UUID of the dependent (target) step
 */
public record PathwayEdgeRequest(
        @NotNull(message = "Source step ID is required") UUID sourceStepId,
        @NotNull(message = "Target step ID is required") UUID targetStepId
) {}
