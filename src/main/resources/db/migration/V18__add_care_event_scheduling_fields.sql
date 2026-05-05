-- Phase 7: Add referral tracking and scheduling coordination fields.
-- Per D-01/D-02: referral_received_at on patients for pathway clock trigger.
-- Per D-04/D-07/D-10/D-13: scheduling fields on care_events for status-aware evaluation.

-- patients: referral received timestamp (not PHI -- no encryption needed)
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS referral_received_at TIMESTAMP WITH TIME ZONE;

-- care_events: scheduling coordination fields
ALTER TABLE care_events
    ADD COLUMN IF NOT EXISTS expected_completion_date DATE,
    ADD COLUMN IF NOT EXISTS scheduling_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS external_facility_name VARCHAR(255);

-- Index for RESULTS_NOT_READY cross-event query (D-08: broad patient-level matching)
CREATE INDEX IF NOT EXISTS idx_care_events_patient_status_expected
    ON care_events(patient_id, status, expected_completion_date)
    WHERE expected_completion_date IS NOT NULL;

GRANT ALL ON patients TO onco_app;
GRANT ALL ON care_events TO onco_app;
