-- One pass per admitted person, so each ticket in a group booking is checked in independently.
-- Neither credential contains personal data:
--   token: random 128-bit value encoded in the QR code
--   code:  short human-readable code for manual entry at the door
CREATE TABLE tickets (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id     UUID         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    ticket_type_id UUID         NOT NULL REFERENCES ticket_types (id),
    event_id       UUID         NOT NULL REFERENCES events (id),
    token          VARCHAR(64)  NOT NULL,
    code           VARCHAR(16)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'VALID',
    -- Mirrors check_ins for display; check_ins is the authoritative, unique record.
    checked_in_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_tickets_token UNIQUE (token),
    CONSTRAINT uk_tickets_code UNIQUE (code),
    CONSTRAINT ck_tickets_status CHECK (status IN ('VALID', 'CANCELLED'))
);

CREATE INDEX idx_tickets_booking ON tickets (booking_id);
CREATE INDEX idx_tickets_event ON tickets (event_id, status);

-- At most one check-in per ticket: the UNIQUE constraint makes duplicate scans impossible.
CREATE TABLE check_ins (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id      UUID        NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    event_id       UUID        NOT NULL REFERENCES events (id),
    checked_in_by  UUID        REFERENCES users (id) ON DELETE SET NULL,
    checked_in_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_check_ins_ticket UNIQUE (ticket_id)
);

CREATE INDEX idx_check_ins_event ON check_ins (event_id, checked_in_at DESC);

-- Backfill passes for bookings made before tickets existed (one row per booked seat).
INSERT INTO tickets (booking_id, ticket_type_id, event_id, token, code, status, created_at)
SELECT b.id,
       bi.ticket_type_id,
       b.event_id,
       encode(gen_random_bytes(16), 'hex'),
       upper(encode(gen_random_bytes(4), 'hex')),
       CASE WHEN b.status IN ('CONFIRMED', 'PENDING') THEN 'VALID' ELSE 'CANCELLED' END,
       b.created_at
FROM bookings b
JOIN booking_items bi ON bi.booking_id = b.id
CROSS JOIN LATERAL generate_series(1, bi.quantity);
