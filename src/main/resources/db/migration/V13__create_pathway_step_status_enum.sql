-- V13__create_pathway_step_status_enum.sql
-- Creates the PostgreSQL enum type for per-patient pathway step status.
-- Used by patient_pathway_steps.status column (V14).
--
-- Values:
--   ACTIVE    - Step is monitored by the pathway evaluation engine
--   PROPOSED  - Step was suggested by AI extraction (Phase 6); skipped during evaluation until confirmed
--   COMPLETED - Step has a matching care event in COMPLETED status
--   SKIPPED   - Step intentionally bypassed (replaces physician_overrides per D-04)

CREATE TYPE pathway_step_status AS ENUM ('ACTIVE', 'PROPOSED', 'COMPLETED', 'SKIPPED');
