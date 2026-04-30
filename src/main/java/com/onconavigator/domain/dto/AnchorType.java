package com.onconavigator.domain.dto;

/**
 * Anchor type for pathway step time window calculation.
 *
 * <p>Used in {@link PathwayStep} JSONB deserialization only — not a PostgreSQL enum type.
 * The anchor type determines the reference point from which {@code windowDays} is measured
 * when evaluating whether a pathway step is overdue:
 *
 * <ul>
 *   <li>{@code DIAGNOSIS_DATE} — window counts from the patient's diagnosis date</li>
 *   <li>{@code PREVIOUS_STEP} — window counts from the completion date of the immediately
 *       preceding pathway step</li>
 *   <li>{@code SPECIFIC_STEP} — window counts from the completion date of the step
 *       identified by {@link PathwayStep#anchorStepId()}</li>
 * </ul>
 */
public enum AnchorType {
    PREVIOUS_STEP,
    DIAGNOSIS_DATE,
    SPECIFIC_STEP
}
