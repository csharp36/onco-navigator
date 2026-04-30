-- V2__create_audit_log.sql
-- Immutable audit log table for HIPAA compliance
-- Application user has INSERT-only access (enforced in V3 permissions migration)

-- Immutable audit log - append-only by design
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_id UUID NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(255),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ip_address INET,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    detail_hash VARCHAR(64),
    request_path VARCHAR(500),
    http_method VARCHAR(10)
);

-- Indexes for compliance queries (actor activity, resource access history, time-range queries)
CREATE INDEX idx_audit_log_actor_timestamp ON audit_log(actor_id, timestamp);
CREATE INDEX idx_audit_log_resource_timestamp ON audit_log(resource_id, timestamp);
CREATE INDEX idx_audit_log_action_timestamp ON audit_log(action, timestamp);
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);

-- Partition preparation: comment for future monthly partitioning
-- ALTER TABLE audit_log RENAME TO audit_log_old;
-- CREATE TABLE audit_log (...) PARTITION BY RANGE (timestamp);
