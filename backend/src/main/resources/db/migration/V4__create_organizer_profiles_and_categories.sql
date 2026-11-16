CREATE TABLE organizer_profiles (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    display_name        VARCHAR(120)  NOT NULL,
    description         VARCHAR(2000),
    contact_email       VARCHAR(255),
    verification_status VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_organizer_profiles_user UNIQUE (user_id),
    CONSTRAINT ck_organizer_profiles_verification CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE TABLE categories (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(60)  NOT NULL,
    slug       VARCHAR(60)  NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

-- Reference data: the platform's fixed category list.
INSERT INTO categories (name, slug, sort_order) VALUES
    ('Music',        'music',      10),
    ('Nightlife',    'nightlife',  20),
    ('Tech',         'tech',       30),
    ('Comedy',       'comedy',     40),
    ('Food & Drink', 'food-drink', 50),
    ('Arts',         'arts',       60),
    ('Sports',       'sports',     70),
    ('Workshops',    'workshops',  80);
