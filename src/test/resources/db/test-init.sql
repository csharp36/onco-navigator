-- test-init.sql
-- Run by Testcontainers PostgreSQLContainer on startup (before Flyway migrations).
-- Creates extensions and roles that the Flyway migrations (especially V3) expect to exist.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Create the roles referenced in V3 migration
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'audit_writer') THEN
        CREATE ROLE audit_writer;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'audit_reader') THEN
        CREATE ROLE audit_reader;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'onco_app') THEN
        CREATE ROLE onco_app;
    END IF;
END
$$;

-- Grant the test user the onco_app role so Flyway V3 GRANT/REVOKE commands
-- have a target; and grant test user full privileges for schema migration
GRANT onco_app TO test;
