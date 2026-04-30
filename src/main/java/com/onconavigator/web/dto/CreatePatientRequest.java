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
        String treatingPhysician
) {}
