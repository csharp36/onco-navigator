-- V16__add_rejected_status_and_ai_source.sql
-- Adds REJECTED to the pathway_step_status PostgreSQL enum and adds source tracking
-- columns to patient_pathway_steps for Phase 6 AI step extraction support.
-- Also adds already_covered_event_types to clinical_documents for D-10 transparency display.
--
-- IMPORTANT: ALTER TYPE ... ADD VALUE is not fully transactional in PostgreSQL.
-- Place it FIRST in this migration. ADD VALUE IF NOT EXISTS is safe in Flyway's
-- default transaction context when it is the first statement (PostgreSQL 16).

ALTER TYPE pathway_step_status ADD VALUE IF NOT EXISTS 'REJECTED';

ALTER TABLE patient_pathway_steps
    ADD COLUMN IF NOT EXISTS source              VARCHAR(50),
    ADD COLUMN IF NOT EXISTS source_document_id  UUID REFERENCES clinical_documents(id),
    ADD COLUMN IF NOT EXISTS proposed_edges_json  TEXT;

ALTER TABLE clinical_documents
    ADD COLUMN IF NOT EXISTS already_covered_event_types TEXT;

CREATE INDEX IF NOT EXISTS idx_pathway_steps_source_doc
    ON patient_pathway_steps(source_document_id)
    WHERE source_document_id IS NOT NULL;

GRANT ALL ON patient_pathway_steps TO onco_app;
GRANT ALL ON clinical_documents TO onco_app;
