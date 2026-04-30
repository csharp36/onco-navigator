package com.onconavigator.domain.enums;

/**
 * Types of care events tracked along a patient's oncology pathway.
 * Maps to the care_event_type PostgreSQL enum.
 */
public enum CareEventType {
    REFERRAL,
    CONSULTATION,
    BIOPSY,
    PATHOLOGY_REPORT,
    IMAGING,
    SURGERY,
    CHEMOTHERAPY,
    RADIATION,
    FOLLOW_UP,
    LAB_WORK,
    GENETIC_TESTING,
    OTHER
}
