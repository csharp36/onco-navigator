-- V4__alter_audit_log_ip_address_type.sql
-- Change audit_log.ip_address from INET to VARCHAR(45)
--
-- Rationale: PostgreSQL's INET type requires native casting (::inet) when inserting from
-- Java, but the JPA entity maps ip_address as a plain String (java.lang.String). Using
-- VARCHAR(45) accommodates both IPv4 (max 15 chars) and IPv6 (max 39 chars) addresses
-- without requiring a custom AttributeConverter, while remaining semantically correct for
-- audit logging purposes (we store IP addresses as strings for display, not routing queries).
--
-- NOTE: PostgreSQL can cast INET to TEXT/VARCHAR automatically for existing data.
ALTER TABLE audit_log
    ALTER COLUMN ip_address TYPE VARCHAR(45) USING ip_address::VARCHAR;
