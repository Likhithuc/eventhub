-- Phase 1 baseline migration.
-- Establishes Flyway's schema history on a fresh database.
-- Domain tables (users, user_roles, events, ...) arrive in later phases as V2__, V3__, ...

-- gen_random_uuid() is built into PostgreSQL 13+; pgcrypto kept for future hashing helpers if needed.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
