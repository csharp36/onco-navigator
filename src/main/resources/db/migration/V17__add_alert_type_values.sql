-- flyway:nonTransactional
-- Phase 7: Add four new alert types for status-aware evaluation.
-- PostgreSQL ALTER TYPE ADD VALUE cannot run inside a transaction block (Flyway 10+).
-- IF NOT EXISTS makes this idempotent (safe to re-run if partially applied).

ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'RESULTS_NOT_READY';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'SCHEDULING_UNCONFIRMED';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'DEADLINE_APPROACHING';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'CANCELLED_EVENT';
