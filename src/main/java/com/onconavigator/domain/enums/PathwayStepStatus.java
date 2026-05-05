package com.onconavigator.domain.enums;

/**
 * Status of a per-patient pathway step.
 * Maps to the pathway_step_status PostgreSQL enum.
 *
 * <p>ACTIVE: Step is monitored by the evaluation engine.
 * PROPOSED: Step was suggested by AI extraction (Phase 6); skipped during evaluation until confirmed.
 * COMPLETED: Step has a matching care event in COMPLETED status.
 * SKIPPED: Step intentionally bypassed (replaces physician_overrides per D-04).
 * REJECTED: Step was proposed by AI extraction and explicitly rejected by a nurse navigator.
 *           Persisted for audit trail and dedup (D-07, D-09).
 */
public enum PathwayStepStatus {
    ACTIVE,
    PROPOSED,
    COMPLETED,
    SKIPPED,
    REJECTED
}
