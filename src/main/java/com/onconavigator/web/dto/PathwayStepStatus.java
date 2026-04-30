package com.onconavigator.web.dto;

import java.time.LocalDate;

/**
 * Status of a single step within a patient's current pathway (per D-08).
 *
 * <p>Provides the data needed to render one row in the patient's pathway timeline on
 * the dashboard. Each step's status is evaluated by PatientService against the care
 * events recorded for the patient.
 *
 * @param stepId          identifier matching the pathway template step (e.g., BREAST_01)
 * @param stepNumber      1-indexed ordinal position in the pathway sequence
 * @param stepName        human-readable name for dashboard display
 * @param status          one of: "COMPLETED", "OVERDUE", "MISSING", "UPCOMING"
 * @param completionDate  date the corresponding care event was recorded; null if not completed
 * @param timingInfo      human-readable timing description (e.g., "14 days late", "Due in 3 days")
 * @param hasActiveAlert  true if there is an OPEN or ACKNOWLEDGED alert for this step
 */
public record PathwayStepStatus(
        String stepId,
        int stepNumber,
        String stepName,
        String status,
        LocalDate completionDate,
        String timingInfo,
        boolean hasActiveAlert
) {}
