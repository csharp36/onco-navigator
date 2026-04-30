#!/bin/bash
set -e

# init-db.sh — Creates application and Temporal databases on PostgreSQL startup.
# This script runs as the postgres superuser during the container's first init.
# The APP_DB_PASSWORD and TEMPORAL_DB_PASSWORD env vars are injected via docker-compose.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Create Temporal database user and schema
    CREATE USER temporal WITH PASSWORD '${TEMPORAL_DB_PASSWORD}';
    CREATE DATABASE temporal OWNER temporal;
    CREATE DATABASE temporal_visibility OWNER temporal;

    -- Create Keycloak database user and schema
    CREATE USER keycloak WITH PASSWORD '${KEYCLOAK_DB_PASSWORD}';
    CREATE DATABASE keycloak OWNER keycloak;

    -- Create application database user and schema
    CREATE USER onco_app WITH PASSWORD '${APP_DB_PASSWORD}';
    CREATE DATABASE onconavigator OWNER onco_app;

    -- Enable pgcrypto for column-level ePHI encryption (HIPAA requirement)
    \c onconavigator
    CREATE EXTENSION IF NOT EXISTS pgcrypto;

    -- Grant application user full access on onconavigator
    GRANT ALL PRIVILEGES ON DATABASE onconavigator TO onco_app;

    -- Audit roles: minimal-privilege access for audit log operations (HIPAA)
    CREATE ROLE audit_writer;
    CREATE ROLE audit_reader;

    -- Assign audit roles to onco_app (requires superuser, so done here not in Flyway)
    GRANT audit_writer TO onco_app;
    GRANT audit_reader TO onco_app;
EOSQL

echo "init-db.sh: databases and roles created successfully."
