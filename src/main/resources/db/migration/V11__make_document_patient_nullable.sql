-- V11__make_document_patient_nullable.sql
-- Allow documents to be uploaded before patient association.
-- Documents can exist in an "unlinked" state during the classification->matching->creation flow.
-- The patient is linked after the user creates or selects a patient.

ALTER TABLE clinical_documents ALTER COLUMN patient_id DROP NOT NULL;
