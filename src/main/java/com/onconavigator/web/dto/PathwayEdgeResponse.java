package com.onconavigator.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a directed edge in a patient's per-patient pathway DAG.
 *
 * <p>An edge {@code sourceStepId -> targetStepId} represents a prerequisite relationship:
 * the target step depends on the source step completing before it can be evaluated.
 *
 * @param id           edge UUID
 * @param pathwayId    UUID of the patient pathway this edge belongs to
 * @param sourceStepId UUID of the prerequisite (source) step
 * @param targetStepId UUID of the dependent (target) step
 * @param createdAt    edge creation timestamp
 */
public record PathwayEdgeResponse(
        UUID id,
        UUID pathwayId,
        UUID sourceStepId,
        UUID targetStepId,
        OffsetDateTime createdAt
) {}
