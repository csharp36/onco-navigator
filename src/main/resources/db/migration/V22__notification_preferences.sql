-- V22__notification_preferences.sql
-- Phase 9: Notification preferences + pending queue tables.

CREATE TYPE notification_channel AS ENUM ('TEAMS', 'EMAIL');

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    is_admin_default BOOLEAN NOT NULL DEFAULT FALSE,
    channel notification_channel NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    alert_type_filter TEXT[] NOT NULL DEFAULT '{}',
    quiet_hours_start INTEGER,
    quiet_hours_end INTEGER,
    timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
    digest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    digest_interval_hours INTEGER NOT NULL DEFAULT 4,
    next_digest_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Partial unique indexes to handle NULL user_id for admin defaults (Pitfall 4)
CREATE UNIQUE INDEX idx_notification_defaults_channel
    ON notification_preferences(channel) WHERE user_id IS NULL AND is_admin_default = TRUE;
CREATE UNIQUE INDEX idx_notification_user_channel
    ON notification_preferences(user_id, channel) WHERE user_id IS NOT NULL;

-- Seed admin defaults: TEAMS enabled for log-only testing (D-05); EMAIL disabled
INSERT INTO notification_preferences (is_admin_default, channel, enabled)
VALUES (TRUE, 'TEAMS', TRUE),
       (TRUE, 'EMAIL', FALSE);

-- Notification pending queue (quiet hours + digest hold) (D-11)
CREATE TABLE notification_pending_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES alerts(id),
    user_id UUID NOT NULL,
    channel notification_channel NOT NULL,
    hold_type VARCHAR(20) NOT NULL CHECK (hold_type IN ('QUIET_HOURS', 'DIGEST')),
    hold_until TIMESTAMP WITH TIME ZONE NOT NULL,
    rendered_content_encrypted BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DISPATCHED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_pending_dispatch
    ON notification_pending_queue(status, hold_until) WHERE status = 'PENDING';

GRANT ALL ON notification_preferences TO onco_app;
GRANT ALL ON notification_pending_queue TO onco_app;
