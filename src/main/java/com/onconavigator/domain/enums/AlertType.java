package com.onconavigator.domain.enums;

/**
 * Types of pathway deviations that trigger alerts.
 * Maps to the alert_type PostgreSQL enum.
 *
 * <p>Phase 7 added: RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED,
 * DEADLINE_APPROACHING, CANCELLED_EVENT for status-aware evaluation.
 */
public enum AlertType {
    MISSING_EVENT,
    DELAYED_EVENT,
    OUT_OF_ORDER,
    // Phase 7: Status-aware evaluation alert types
    RESULTS_NOT_READY,
    SCHEDULING_UNCONFIRMED,
    DEADLINE_APPROACHING,
    CANCELLED_EVENT
}
