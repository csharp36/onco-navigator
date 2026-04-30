package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.domain.enums.PatientStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for patient data returned to the nurse navigator dashboard.
 *
 * <p>Fields {@code firstName}, {@code lastName}, {@code dateOfBirth}, and {@code mrn}
 * are decrypted server-side from their {@code BYTEA} columns before being placed in this
 * record. They are transmitted over TLS to authenticated JWT holders only.
 *
 * <p>HIPAA note: Never log these fields on the backend. The frontend must not store
 * them in plaintext logs or local storage beyond the session.
 *
 * @param summaryStatus computed by PatientService — one of: "On Track", "Alert Active", "Inactive"
 */
public record PatientResponse(
        UUID id,
        String firstName,
        String lastName,
        String dateOfBirth,
        String mrn,
        CancerType cancerType,
        String cancerStage,
        LocalDate diagnosisDate,
        UUID assignedNavigatorId,
        String treatingPhysician,
        PatientStatus status,
        String summaryStatus,
        OffsetDateTime createdAt
) {}
