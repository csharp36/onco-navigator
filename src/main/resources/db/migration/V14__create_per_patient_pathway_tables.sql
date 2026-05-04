-- V14__create_per_patient_pathway_tables.sql
-- Creates three tables for the per-patient pathway DAG data model (Phase 5).
--
-- Tables:
--   patient_pathways       - one row per patient, links patient to optional source template
--   patient_pathway_steps  - ordered steps derived from the template or AI extraction
--   patient_pathway_edges  - directed edges forming the DAG (prerequisite relationships)
--
-- Design notes:
--   - patient_pathways has UNIQUE(patient_id): each patient has exactly one active pathway
--   - edges reference steps by UUID columns (not FK to allow lightweight edge representation)
--   - ON DELETE CASCADE ensures edges and steps are removed if a pathway is deleted
--   - created_by uses system UUID '00000000-0000-0000-0000-000000000000' for migration-created rows
--   - version column on steps provides optimistic locking for concurrent status updates

-- -------------------------------------------------------------------------
-- patient_pathways
-- -------------------------------------------------------------------------
CREATE TABLE patient_pathways (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id               UUID NOT NULL REFERENCES patients(id),
    source_template_id       UUID REFERENCES pathway_templates(id),
    source_template_version  INTEGER,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by               UUID NOT NULL,
    CONSTRAINT uq_patient_pathways_patient UNIQUE (patient_id)
);

-- -------------------------------------------------------------------------
-- patient_pathway_steps
-- -------------------------------------------------------------------------
CREATE TABLE patient_pathway_steps (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pathway_id               UUID NOT NULL REFERENCES patient_pathways(id) ON DELETE CASCADE,
    name                     VARCHAR(255) NOT NULL,
    description              TEXT,
    event_type               care_event_type,
    window_days              INTEGER,
    required                 BOOLEAN NOT NULL DEFAULT true,
    status                   pathway_step_status NOT NULL DEFAULT 'ACTIVE',
    skip_reason              TEXT,
    alert_text               TEXT,
    suggested_action         TEXT,
    source_template_step_id  VARCHAR(100),
    completed_at             TIMESTAMP WITH TIME ZONE,
    completed_care_event_id  UUID REFERENCES care_events(id),
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by               UUID NOT NULL,
    version                  INTEGER NOT NULL DEFAULT 0
);

-- -------------------------------------------------------------------------
-- patient_pathway_edges
-- -------------------------------------------------------------------------
CREATE TABLE patient_pathway_edges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pathway_id      UUID NOT NULL REFERENCES patient_pathways(id) ON DELETE CASCADE,
    source_step_id  UUID NOT NULL REFERENCES patient_pathway_steps(id) ON DELETE CASCADE,
    target_step_id  UUID NOT NULL REFERENCES patient_pathway_steps(id) ON DELETE CASCADE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by      UUID NOT NULL,
    CONSTRAINT uq_pathway_edge UNIQUE (source_step_id, target_step_id),
    CONSTRAINT chk_no_self_edge CHECK (source_step_id <> target_step_id)
);

-- -------------------------------------------------------------------------
-- Indexes
-- -------------------------------------------------------------------------
CREATE INDEX idx_patient_pathway_steps_pathway ON patient_pathway_steps(pathway_id);
CREATE INDEX idx_patient_pathway_steps_status  ON patient_pathway_steps(status);
CREATE INDEX idx_patient_pathway_edges_pathway ON patient_pathway_edges(pathway_id);
CREATE INDEX idx_patient_pathway_edges_source  ON patient_pathway_edges(source_step_id);
CREATE INDEX idx_patient_pathway_edges_target  ON patient_pathway_edges(target_step_id);

-- -------------------------------------------------------------------------
-- Grants
-- -------------------------------------------------------------------------
GRANT ALL ON patient_pathways       TO onco_app;
GRANT ALL ON patient_pathway_steps  TO onco_app;
GRANT ALL ON patient_pathway_edges  TO onco_app;
