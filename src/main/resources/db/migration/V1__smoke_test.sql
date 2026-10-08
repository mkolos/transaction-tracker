-- Throwaway table used only by the Week 1 smoke test.
-- It proves Flyway runs and that NUMERIC(19,4) round-trips exactly.
-- Task 2 replaces this file with the real schema (safe while no persistent data exists).
CREATE TABLE smoke_money (
    id     BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19, 4) NOT NULL
);
