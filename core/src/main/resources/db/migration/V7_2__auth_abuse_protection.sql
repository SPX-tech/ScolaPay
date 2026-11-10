-- Indépendant de V7 (#26) et V7_1 (#27). Intégrer les trois avant toute base persistante.
-- Six dimensions disposent chacune de 65 536 compartiments ; les deux budgets globaux d'un seul.
CREATE TABLE auth_abuse_buckets (
    scope smallint NOT NULL CHECK (scope BETWEEN 0 AND 7),
    slot integer NOT NULL CHECK (slot BETWEEN 0 AND 65535),
    tat timestamptz NOT NULL,
    PRIMARY KEY (scope, slot),
    CHECK (scope NOT IN (1, 5) OR slot = 0)
);
CREATE INDEX auth_abuse_refilled ON auth_abuse_buckets (tat);
