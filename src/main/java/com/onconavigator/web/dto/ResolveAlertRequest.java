package com.onconavigator.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for resolving an open alert (per ALRT-04).
 *
 * <p>Resolution notes document the corrective action taken by the nurse navigator.
 * These notes are stored in the {@code alerts.resolution_notes} column and are included
 * in the audit trail. Notes should describe clinical process actions, not raw PHI.
 */
public record ResolveAlertRequest(
        @NotBlank(message = "Resolution notes are required") String notes
) {}
