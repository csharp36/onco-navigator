package com.onconavigator.domain.enums;

/**
 * Lifecycle status of a nurse-navigator alert.
 * Maps to the alert_status PostgreSQL enum.
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED
}
