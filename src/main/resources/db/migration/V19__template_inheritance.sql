-- V19__template_inheritance.sql
-- Phase 8: Add template inheritance support.
-- Enables parent/child template relationships (D-02: general-purpose inheritance).
-- Removes UNIQUE constraint on cancer_type (multiple templates per cancer type).
-- Adds parent_template_id, name, description columns.

-- 1. Drop UNIQUE constraint on cancer_type (allows multiple templates per cancer type)
ALTER TABLE pathway_templates DROP CONSTRAINT IF EXISTS pathway_templates_cancer_type_key;

-- 2. Add inheritance and display columns
ALTER TABLE pathway_templates ADD COLUMN parent_template_id UUID REFERENCES pathway_templates(id);
ALTER TABLE pathway_templates ADD COLUMN name VARCHAR(255);
ALTER TABLE pathway_templates ADD COLUMN description TEXT;

-- 3. Backfill name for existing root templates
UPDATE pathway_templates SET name = 'Breast Cancer Pathway' WHERE cancer_type = 'BREAST';
UPDATE pathway_templates SET name = 'Lung Cancer Pathway' WHERE cancer_type = 'LUNG';
UPDATE pathway_templates SET name = 'Colorectal Cancer Pathway' WHERE cancer_type = 'COLORECTAL';

-- 4. Make name NOT NULL after backfill
ALTER TABLE pathway_templates ALTER COLUMN name SET NOT NULL;

-- 5. Index for parent lookups and cancer_type queries (UNIQUE removed, index needed)
CREATE INDEX idx_pathway_templates_parent ON pathway_templates(parent_template_id);
CREATE INDEX idx_pathway_templates_cancer_type ON pathway_templates(cancer_type);

-- 6. Mirror changes on Hibernate Envers audit table (Pitfall 4: _AUD must match entity)
ALTER TABLE pathway_templates_aud ADD COLUMN parent_template_id UUID;
ALTER TABLE pathway_templates_aud ADD COLUMN name VARCHAR(255);
ALTER TABLE pathway_templates_aud ADD COLUMN description TEXT;

-- 7. Grants
GRANT ALL ON pathway_templates TO onco_app;
