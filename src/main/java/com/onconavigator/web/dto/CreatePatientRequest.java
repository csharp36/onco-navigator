package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CancerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for enrolling a new patient in pathway monitoring.
 *
 * <p>All PHI fields (firstName, lastName, dateOfBirth, mrn) are validated for presence
 * here but encrypted at the JPA converter boundary before being written to the database.
 * Never log these fields.
 *
 * <p>Per D-01: manual patient registration is the V1 data entry path (no EMR integration).
 *
 * <p>The optional {@code pathwayMode} field controls how the per-patient pathway DAG is
 * initialised at enrollment time (D-07):
 * <ul>
 *   <li>{@code "template"} (default) — deep-copies the cancer-type template into per-patient rows
 *   <li>{@code "empty"} — creates a pathway with no steps; AI extraction populates it
 * </ul>
 *
 * <p>The optional {@code templateId} field selects a specific template variant (including
 * child templates). When provided, selects a specific template variant for the patient's
 * pathway. When null, the root template for the patient's cancer type is used (backward
 * compatible with existing API clients).
 */
public record CreatePatientRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @NotBlank(message = "Date of birth is required") String dateOfBirth,
        @NotBlank(message = "MRN is required") String mrn,
        @NotNull(message = "Cancer type is required") CancerType cancerType,
        @NotBlank(message = "Cancer stage is required") String cancerStage,
        @NotNull(message = "Diagnosis date is required") LocalDate diagnosisDate,
        UUID assignedNavigatorId,
        String treatingPhysician,
        UUID templateId,  // Optional: specific template variant UUID. Null = root template for cancer type.
        String pathwayMode  // "template" (default) or "empty" per D-07
) {
    /**
     * Returns the effective pathway mode, defaulting to "template" for backward compatibility.
     *
     * <p>Callers that do not provide {@code pathwayMode} (e.g., existing API clients) continue
     * to receive template-based pathway initialisation without any change.
     *
     * @return "template" or "empty"
     */
    public String effectivePathwayMode() {
        return pathwayMode == null || pathwayMode.isBlank() ? "template" : pathwayMode;
    }
}
