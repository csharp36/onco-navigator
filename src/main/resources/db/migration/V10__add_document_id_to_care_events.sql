-- V10__add_document_id_to_care_events.sql
-- Add optional document link from care_events to clinical_documents (DOC-04, DOC-05).
-- A care event may optionally reference the clinical document that was the source of the event.
-- ON DELETE SET NULL: if the document is deleted, the care event remains but loses the link.

ALTER TABLE care_events ADD COLUMN IF NOT EXISTS document_id UUID REFERENCES clinical_documents(id) ON DELETE SET NULL;

CREATE INDEX idx_care_events_document_id ON care_events(document_id);
