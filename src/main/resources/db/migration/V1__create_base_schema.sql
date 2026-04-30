-- V1__create_base_schema.sql
-- Core application schema for Onco-Navigator
-- PHI fields stored as encrypted bytea (AES-GCM via JPA EncryptionConverter)

-- Enums as PostgreSQL types
CREATE TYPE cancer_type AS ENUM ('BREAST', 'LUNG', 'COLORECTAL');
CREATE TYPE care_event_type AS ENUM (
    'REFERRAL', 'CONSULTATION', 'BIOPSY', 'PATHOLOGY_REPORT',
    'IMAGING', 'SURGERY', 'CHEMOTHERAPY', 'RADIATION',
    'FOLLOW_UP', 'LAB_WORK', 'GENETIC_TESTING', 'OTHER'
);
CREATE TYPE care_event_status AS ENUM ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'PENDING');
CREATE TYPE alert_type AS ENUM ('MISSING_EVENT', 'DELAYED_EVENT', 'OUT_OF_ORDER');
CREATE TYPE alert_status AS ENUM ('OPEN', 'ACKNOWLEDGED', 'RESOLVED');
CREATE TYPE patient_status AS ENUM ('ACTIVE', 'INACTIVE', 'DECEASED', 'TRANSFERRED');

-- Patients table - PHI fields stored as encrypted bytea (HIPAA: encryption at rest)
CREATE TABLE patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name_encrypted BYTEA NOT NULL,
    last_name_encrypted BYTEA NOT NULL,
    date_of_birth_encrypted BYTEA NOT NULL,
    mrn_encrypted BYTEA NOT NULL,
    cancer_type cancer_type NOT NULL,
    cancer_stage VARCHAR(10) NOT NULL,
    diagnosis_date DATE NOT NULL,
    assigned_navigator_id UUID,
    treating_physician VARCHAR(255),
    status patient_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    CONSTRAINT chk_cancer_stage CHECK (cancer_stage ~ '^(I|II|III|IV)(A|B|C)?$')
);

-- Care events
CREATE TABLE care_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    event_type care_event_type NOT NULL,
    event_date DATE NOT NULL,
    status care_event_status NOT NULL DEFAULT 'PENDING',
    notes_encrypted BYTEA,
    pathway_step_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL
);

-- Alerts
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    alert_type alert_type NOT NULL,
    status alert_status NOT NULL DEFAULT 'OPEN',
    pathway_step_name VARCHAR(255) NOT NULL,
    deviation_description TEXT NOT NULL,
    suggested_action TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    resolution_notes TEXT,
    workflow_run_id VARCHAR(255)
);

-- Pathway templates (JSON-based, config-as-data)
CREATE TABLE pathway_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cancer_type cancer_type NOT NULL UNIQUE,
    version INTEGER NOT NULL DEFAULT 1,
    template_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL
);

-- Indexes for common query patterns
CREATE INDEX idx_patients_status ON patients(status);
CREATE INDEX idx_patients_cancer_type ON patients(cancer_type);
CREATE INDEX idx_care_events_patient_id ON care_events(patient_id);
CREATE INDEX idx_care_events_event_date ON care_events(event_date);
CREATE INDEX idx_alerts_patient_id ON alerts(patient_id);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_status_created ON alerts(status, created_at DESC);
