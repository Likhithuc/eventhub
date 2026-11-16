CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE events (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID          NOT NULL REFERENCES organizer_profiles (id),
    category_id  UUID          NOT NULL REFERENCES categories (id),
    title        VARCHAR(150)  NOT NULL,
    description  VARCHAR(5000) NOT NULL,
    venue_name   VARCHAR(150)  NOT NULL,
    address      VARCHAR(300)  NOT NULL,
    city         VARCHAR(80)   NOT NULL,
    starts_at    TIMESTAMPTZ   NOT NULL,
    ends_at      TIMESTAMPTZ   NOT NULL,
    -- S3 object key of the banner image (added with uploads); never a public file path.
    banner_key   VARCHAR(300),
    status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_events_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_events_status CHECK (status IN
        ('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'REJECTED', 'CANCELLED', 'COMPLETED'))
);

-- Public discovery: published events ordered by start time.
CREATE INDEX idx_events_status_starts_at ON events (status, starts_at);
CREATE INDEX idx_events_city_lower ON events (lower(city));
CREATE INDEX idx_events_category ON events (category_id);
CREATE INDEX idx_events_organizer ON events (organizer_id);
-- Case-insensitive substring search on title (ILIKE '%term%').
CREATE INDEX idx_events_title_trgm ON events USING gin (lower(title) gin_trgm_ops);

CREATE TABLE ticket_types (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       UUID          NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    name           VARCHAR(80)   NOT NULL,
    price          NUMERIC(12,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL DEFAULT 'INR',
    total_quantity INT           NOT NULL,
    sales_start_at TIMESTAMPTZ,
    sales_end_at   TIMESTAMPTZ,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_ticket_types_event_name UNIQUE (event_id, name),
    CONSTRAINT ck_ticket_types_price CHECK (price >= 0),
    CONSTRAINT ck_ticket_types_quantity CHECK (total_quantity > 0),
    CONSTRAINT ck_ticket_types_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ticket_types_sales_window CHECK (
        sales_start_at IS NULL OR sales_end_at IS NULL OR sales_end_at > sales_start_at),
    CONSTRAINT ck_ticket_types_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_ticket_types_event ON ticket_types (event_id);
