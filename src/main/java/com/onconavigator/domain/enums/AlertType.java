package com.onconavigator.domain.enums;

/**
 * Types of pathway deviations that trigger alerts.
 * Maps to the alert_type PostgreSQL enum.
 */
public enum AlertType {
    MISSING_EVENT,
    DELAYED_EVENT,
    OUT_OF_ORDER
}
