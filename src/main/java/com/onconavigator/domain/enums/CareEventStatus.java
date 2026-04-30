package com.onconavigator.domain.enums;

/**
 * Status of a care event in a patient's pathway.
 * Maps to the care_event_status PostgreSQL enum.
 */
public enum CareEventStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    PENDING
}
