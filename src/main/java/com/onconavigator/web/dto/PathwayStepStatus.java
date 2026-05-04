package com.onconavigator.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Status of a single step within a patient's per-patient pathway (Phase 5 DAG).
 *
 * <p>Provides the data needed to render one node in the patient's pathway DAG visualization
 * on the dashboard. Each step carries its DAG position (depth, sortOrder), lifecycle status,
 * timing information, and the prerequisite step IDs needed for edge rendering.
 *
 * @param stepId              UUID of the per-patient pathway step
 * @param stepName            human-readable step name
 * @param status              step lifecycle status: ACTIVE, COMPLETED, PROPOSED, SKIPPED
 * @param depth               DAG depth (0 = root, 1 = depends on root, etc.)
 * @param sortOrder           position in topological sort order
 * @param completionDate      date the step was marked completed; null if not completed
 * @param timingInfo          human-readable timing (e.g., "Due in 3 days", "14 days overdue")
 * @param hasActiveAlert      true if an OPEN alert exists for this step
 * @param skipReason          reason if status is SKIPPED; null otherwise
 * @param prerequisiteStepIds UUIDs of prerequisite steps (for frontend edge rendering)
 */
public record PathwayStepStatus(
        String stepId,
        String stepName,
        String status,
        int depth,
        int sortOrder,
        LocalDate completionDate,
        String timingInfo,
        boolean hasActiveAlert,
        String skipReason,
        List<String> prerequisiteStepIds
) {}
