-- V5__create_physician_overrides.sql
-- Physician override records to suppress false-positive pathway alerts (D-11)
-- When a physician intentionally reorders or skips a pathway step, this record
-- tells the workflow engine not to generate an alert for that patient + step.
--
-- No PHI is stored here: patient referenced by UUID, override_reason contains
-- clinical process text (e.g., "physician ordered alternative sequencing"),
-- not patient-identifying data.

CREATE TABLE physician_overrides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    pathway_step_id VARCHAR(100) NOT NULL,
    override_reason TEXT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Primary lookup: check if override exists for a specific patient + step.
-- UNIQUE enforces one active override per patient per step (no duplicate suppression).
CREATE UNIQUE INDEX idx_physician_overrides_patient_step
    ON physician_overrides(patient_id, pathway_step_id);

-- Grant permissions to application user (follows V3 convention)
GRANT ALL ON physician_overrides TO onco_app;
