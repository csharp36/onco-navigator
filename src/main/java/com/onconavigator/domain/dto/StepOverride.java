package com.onconavigator.domain.dto;

import java.util.Map;

/**
 * A field-level override for a single parent pathway step in a child template diff.
 *
 * <p>The {@code stepId} identifies which parent step to modify. The {@code fields} map
 * contains only the fields that differ from the parent -- non-overridden fields inherit
 * the parent's values at merge time (live inheritance, D-06).
 *
 * <p>Supported field keys match {@link PathwayStep} record component names:
 * name, description, eventType, windowDays, anchorType, anchorStepId, required,
 * alertText, suggestedAction, prerequisites.
 *
 * @param stepId the identifier of the parent step to override
 * @param fields map of field names to their overridden values
 */
public record StepOverride(String stepId, Map<String, Object> fields) {
}
