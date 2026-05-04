package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CareEventType;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating or updating a step in a patient's per-patient pathway.
 *
 * <p>All fields except {@code name} are optional to allow partial updates. The
 * {@code eventType} must match a valid {@link CareEventType} when provided.
 *
 * <p>Per D-12: new steps default to ACTIVE status and no prerequisites (root node).
 * Prerequisites are added separately via the edge API.
 *
 * @param name            human-readable step name (required)
 * @param description     clinical description of the step (optional)
 * @param eventType       care event type that satisfies this step (optional)
 * @param windowDays      expected completion window in days (optional)
 * @param required        whether this step is required for pathway completion (optional, defaults to true)
 * @param alertText       deviation alert text shown to nurse navigators (optional)
 * @param suggestedAction corrective action text included in the alert (optional)
 */
public record PathwayStepRequest(
        @NotBlank(message = "Step name is required") String name,
        String description,
        CareEventType eventType,
        Integer windowDays,
        Boolean required,
        String alertText,
        String suggestedAction
) {}
