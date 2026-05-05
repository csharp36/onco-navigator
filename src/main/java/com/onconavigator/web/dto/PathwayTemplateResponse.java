package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CancerType;

import java.util.UUID;

/**
 * Response DTO for pathway template listing.
 * Templates are non-PHI clinical protocol configuration.
 *
 * @param id               template UUID
 * @param cancerType       cancer type this template applies to
 * @param name             human-readable template name
 * @param description      1-line clinical description (null for root templates)
 * @param parentTemplateId parent template UUID (null for root templates)
 * @param version          template version number
 * @param isRoot           true if this is a root template (parentTemplateId is null)
 */
public record PathwayTemplateResponse(
        UUID id,
        CancerType cancerType,
        String name,
        String description,
        UUID parentTemplateId,
        int version,
        boolean isRoot
) {}
