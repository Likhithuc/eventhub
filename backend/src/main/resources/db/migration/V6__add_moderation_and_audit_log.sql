-- Moderation (review) fields on events.
ALTER TABLE events
    ADD COLUMN submitted_at     TIMESTAMPTZ,
    ADD COLUMN reviewed_at      TIMESTAMPTZ,
    ADD COLUMN reviewed_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN rejection_reason VARCHAR(1000);

-- Admin approval queue: pending events, oldest submission first.
CREATE INDEX idx_events_pending_queue ON events (status, submitted_at);

-- Append-only record of important admin actions.
CREATE TABLE admin_audit_log (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID          REFERENCES users (id) ON DELETE SET NULL,
    action      VARCHAR(50)   NOT NULL,
    target_type VARCHAR(30)   NOT NULL,
    target_id   UUID          NOT NULL,
    summary     VARCHAR(300)  NOT NULL,
    details     VARCHAR(1000),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_log_created ON admin_audit_log (created_at DESC);
CREATE INDEX idx_admin_audit_log_target ON admin_audit_log (target_type, target_id);
