package com.onconavigator.web.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a single step in a patient's per-patient pathway DAG.
 *
 * <p>Includes topological ordering metadata ({@code depth}, {@code sortOrder}) computed
 * at query time by Kahn's algorithm so the frontend can render the DAG without additional
 * graph traversal work.
 *
 * @param id                   step UUID
 * @param pathwayId            UUID of the patient pathway this step belongs to
 * @param name                 human-readable step name
 * @param description          clinical description of the step
 * @param eventType            care event type name that satisfies this step (nullable)
 * @param windowDays           expected completion window in days (nullable)
 * @param required             whether this step is required for pathway completion
 * @param status               current evaluation status (ACTIVE, PROPOSED, COMPLETED, SKIPPED)
 * @param skipReason           reason recorded when the step was skipped (nullable)
 * @param alertText            deviation alert text shown to nurse navigators
 * @param suggestedAction      corrective action text included in alerts
 * @param completedAt          timestamp when the step was completed (nullable)
 * @param completedCareEventId UUID of the care event that completed this step (nullable)
 * @param depth                DAG depth: 0 for root nodes, max(predecessor depths)+1 for others
 * @param sortOrder            topological sort order position across the entire pathway
 * @param prerequisiteIds      UUIDs of direct prerequisite steps (incoming edges)
 * @param createdAt            step creation timestamp
 */
public record PathwayStepResponse(
        UUID id,
        UUID pathwayId,
        String name,
        String description,
        String eventType,
        Integer windowDays,
        boolean required,
        String status,
        String skipReason,
        String alertText,
        String suggestedAction,
        OffsetDateTime completedAt,
        UUID completedCareEventId,
        int depth,
        int sortOrder,
        List<UUID> prerequisiteIds,
        OffsetDateTime createdAt
) {}
