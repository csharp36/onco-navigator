-- V7__alert_dedup_index.sql
-- Add partial UNIQUE index on alerts to enforce at-most-one OPEN alert per (patient, step).
--
-- Without this index, the application-level deduplication check in PathwayEvaluationActivityImpl
-- is a TOCTOU race: two concurrent Temporal activity retries can both pass the existence check
-- before either commits, resulting in duplicate OPEN alerts for the same patient+step pair.
--
-- The partial index (WHERE status = 'OPEN') allows multiple RESOLVED alerts over time for
-- the same patient+step pair (e.g., alert resolved, patient re-enrolled, new alert created).
-- Only one OPEN alert per pair is permitted at any time.

CREATE UNIQUE INDEX idx_alerts_open_dedup
    ON alerts(patient_id, pathway_step_name)
    WHERE status = 'OPEN';
