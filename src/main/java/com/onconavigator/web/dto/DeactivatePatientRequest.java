package com.onconavigator.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for deactivating (soft-deleting) a patient from active monitoring (per DATA-04).
 *
 * <p>A reason is required for audit trail purposes — the reason is stored in audit history
 * via Hibernate Envers but should not contain PHI (use clinical process reasons, not patient data).
 */
public record DeactivatePatientRequest(
        @NotBlank(message = "Reason is required") String reason
) {}
