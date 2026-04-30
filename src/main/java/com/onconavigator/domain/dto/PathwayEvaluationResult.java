package com.onconavigator.domain.dto;

import java.util.List;

/**
 * Result of pathway evaluation activity.
 *
 * <p>Contains no PHI — only step completion status and alert summary strings.
 * Alert summary strings are pathway process labels (e.g., "BREAST_01: Surgery Report"),
 * not patient-specific clinical content.
 *
 * <p>This record is serialized by Temporal's Jackson-based data converter when returned
 * from {@link com.onconavigator.activity.PathwayEvaluationActivity#evaluate}. The record
 * component names are used as JSON field names.
 */
public record PathwayEvaluationResult(boolean allStepsComplete, List<String> alertsGenerated) {
    // Compact canonical constructor — no additional validation needed.
    // alertsGenerated may be empty (no deviations found) but should never be null.
}
