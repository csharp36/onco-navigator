-- V3__audit_permissions.sql
-- Restrict audit_log table: application user can only INSERT, never UPDATE or DELETE
-- This makes the audit trail immutable from the application layer (HIPAA tamper-resistance)

-- Grant INSERT-only to the audit_writer role (created in docker/init-db.sh)
GRANT INSERT ON audit_log TO audit_writer;
GRANT USAGE, SELECT ON SEQUENCE audit_log_id_seq TO audit_writer;

-- Grant SELECT-only to audit_reader role (for compliance queries and reporting)
GRANT SELECT ON audit_log TO audit_reader;

-- Assign audit_writer to the application user
GRANT audit_writer TO onco_app;

-- Explicitly revoke dangerous permissions from onco_app on audit_log
-- This is the critical HIPAA control: no application code can alter audit history
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM onco_app;

-- The onco_app user retains full CRUD on all other application tables
GRANT ALL ON patients, care_events, alerts, pathway_templates TO onco_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO onco_app;
