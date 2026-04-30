package com.onconavigator.domain.dto;

import com.onconavigator.domain.enums.CareEventType;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Immutable representation of a single step within a pathway template.
 *
 * <p>Deserialised from the {@code template_data} JSONB column of {@code pathway_templates}
 * using Jackson's canonical record constructor mapping. Field names match the JSONB keys
 * defined in the V6 seed migration (D-04 schema).
 *
 * <p>No PHI is contained here — pathway steps describe clinical process expectations
 * (event types, time windows, alert text), not patient-specific data.
 *
 * @param stepId          unique identifier for this step within the pathway (e.g., BREAST_01)
 * @param stepNumber      ordinal position in the pathway sequence (1-indexed)
 * @param name            human-readable step name displayed in alerts and the dashboard
 * @param description     clinical description of what this step entails
 * @param eventType       the care event type that satisfies this step
 * @param windowDays      maximum days from the anchor before this step is considered overdue
 * @param anchorType      reference point from which {@code windowDays} is measured
 * @param anchorStepId    stepId of the anchor step; non-null only when anchorType is SPECIFIC_STEP
 * @param required        whether this step is required (true) or optional (false)
 * @param alertText       plain-language deviation description sent to the nurse navigator
 * @param suggestedAction corrective action text included in the alert
 * @param prerequisites   stepIds that must be in Completed status before this step can occur
 */
public record PathwayStep(
        String stepId,
        int stepNumber,
        String name,
        String description,
        CareEventType eventType,
        int windowDays,
        AnchorType anchorType,
        @Nullable String anchorStepId,
        boolean required,
        String alertText,
        String suggestedAction,
        List<String> prerequisites
) {
}
