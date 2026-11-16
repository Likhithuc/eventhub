-- Per-event cancellation rule: attendees may cancel until this many hours before the start.
ALTER TABLE events
    ADD COLUMN cancellation_cutoff_hours INT NOT NULL DEFAULT 24,
    ADD CONSTRAINT ck_events_cancellation_cutoff CHECK (cancellation_cutoff_hours BETWEEN 0 AND 720);

-- Inventory counter, updated in the same transaction as the booking while the row is locked.
-- The CHECK is the last line of defence: the database itself refuses to oversell.
ALTER TABLE ticket_types
    ADD COLUMN sold_quantity INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_ticket_types_sold CHECK (sold_quantity >= 0 AND sold_quantity <= total_quantity);

CREATE TABLE bookings (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_reference   VARCHAR(12)   NOT NULL,
    user_id             UUID          NOT NULL REFERENCES users (id),
    event_id            UUID          NOT NULL REFERENCES events (id),
    status              VARCHAR(20)   NOT NULL,
    ticket_count        INT           NOT NULL,
    total_amount        NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'INR',
    -- Client-supplied key so a retried request returns the original booking instead of a duplicate.
    idempotency_key     VARCHAR(64),
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(300),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_bookings_reference UNIQUE (booking_reference),
    CONSTRAINT uk_bookings_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'REFUNDED')),
    CONSTRAINT ck_bookings_ticket_count CHECK (ticket_count > 0),
    CONSTRAINT ck_bookings_total CHECK (total_amount >= 0)
);

CREATE INDEX idx_bookings_user ON bookings (user_id, created_at DESC);
CREATE INDEX idx_bookings_event ON bookings (event_id, status);

CREATE TABLE booking_items (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id     UUID          NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    ticket_type_id UUID          NOT NULL REFERENCES ticket_types (id),
    quantity       INT           NOT NULL,
    -- Price snapshot at booking time; later price edits don't change past bookings.
    unit_price     NUMERIC(12,2) NOT NULL,
    CONSTRAINT uk_booking_items_type UNIQUE (booking_id, ticket_type_id),
    CONSTRAINT ck_booking_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_booking_items_price CHECK (unit_price >= 0)
);

CREATE INDEX idx_booking_items_ticket_type ON booking_items (ticket_type_id);
