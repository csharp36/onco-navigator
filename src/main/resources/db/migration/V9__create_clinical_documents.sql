-- V9__create_clinical_documents.sql
-- Create clinical_documents table for storing uploaded clinical PDFs/images (DOC-05).
-- Documents are stored as BYTEA blobs linked to care events and patients.
-- extracted_text is stored encrypted (column-level via JPA EncryptionConverter).

CREATE TABLE clinical_documents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    care_event_id           UUID REFERENCES care_events(id) ON DELETE SET NULL,
    patient_id              UUID NOT NULL REFERENCES patients(id),
    original_filename       TEXT NOT NULL,
    content_type            TEXT NOT NULL,
    file_size_bytes         BIGINT NOT NULL,
    document_type           TEXT,
    classification_source   TEXT,
    content                 BYTEA NOT NULL,
    extracted_text_encrypted BYTEA,
    extraction_confidence   INT,
    created_by              UUID NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clinical_documents_patient_id ON clinical_documents(patient_id);
CREATE INDEX idx_clinical_documents_care_event_id ON clinical_documents(care_event_id);
