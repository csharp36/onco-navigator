package com.onconavigator.domain.enums;

/**
 * Enrollment status of a patient in the monitoring system.
 * Maps to the patient_status PostgreSQL enum.
 */
public enum PatientStatus {
    ACTIVE,
    INACTIVE,
    DECEASED,
    TRANSFERRED
}
