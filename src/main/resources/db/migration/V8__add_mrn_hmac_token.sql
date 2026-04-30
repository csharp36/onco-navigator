-- V8__add_mrn_hmac_token.sql
-- Add deterministic HMAC index token for MRN equality search (per D-04).
-- MRN is AES-GCM encrypted with random IV making equality queries impossible.
-- HMAC-SHA256 token is deterministic and non-reversible — enables exact MRN lookup.

ALTER TABLE patients ADD COLUMN mrn_hmac_token VARCHAR(64);
CREATE INDEX idx_patients_mrn_hmac_token ON patients(mrn_hmac_token);
