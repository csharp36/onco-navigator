-- V23__notification_log.sql
-- Phase 9: Immutable notification dispatch audit trail.

CREATE TABLE notification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES alerts(id),
    user_id UUID NOT NULL,
    channel notification_channel NOT NULL,
    rendered_content BYTEA NOT NULL,   -- AES-GCM encrypted (contains PHI: patient name + MRN)
    is_digest BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'SENT',
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_log_alert ON notification_log(alert_id);
CREATE INDEX idx_notification_log_user ON notification_log(user_id, sent_at DESC);

-- Envers audit table for notification_log (D-10: @Audited for PHI-containing entity)
CREATE TABLE notification_log_aud (
    id UUID NOT NULL,
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    alert_id UUID,
    user_id UUID,
    channel notification_channel,
    rendered_content BYTEA,
    is_digest BOOLEAN,
    status VARCHAR(20),
    sent_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id, rev),
    FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

GRANT ALL ON notification_log TO onco_app;
GRANT ALL ON notification_log_aud TO onco_app;
