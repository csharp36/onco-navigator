-- V21__add_alert_missing_summary.sql
-- Phase 9: Add missing_summary column to alerts table.
-- Column is TEXT (no DB-level constraint -- service layer enforces <=150 chars per D-02).
-- Backfills existing rows from first 150 chars of deviation_description (D-03).
-- Truncates existing suggested_action values exceeding 150 chars (D-03).

ALTER TABLE alerts ADD COLUMN IF NOT EXISTS missing_summary TEXT;

-- Backfill: derive missing_summary from first 150 chars of deviation_description
UPDATE alerts SET missing_summary = LEFT(deviation_description, 150)
WHERE missing_summary IS NULL;

-- Truncate existing suggested_action values exceeding 150 chars (D-03)
UPDATE alerts SET suggested_action = LEFT(suggested_action, 150)
WHERE suggested_action IS NOT NULL AND LENGTH(suggested_action) > 150;

-- Mirror on Envers AUD table (Pitfall 5: _AUD must match entity schema)
ALTER TABLE alerts_aud ADD COLUMN IF NOT EXISTS missing_summary TEXT;

GRANT ALL ON alerts TO onco_app;
