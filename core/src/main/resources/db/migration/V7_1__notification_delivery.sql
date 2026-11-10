-- Version distincte de V7 (Mission 003, lot 6). Aucune table financière modifiée.
CREATE TABLE notification_messages (
    id uuid PRIMARY KEY,
    category varchar(48) NOT NULL,
    channel varchar(8) NOT NULL CHECK (channel IN ('EMAIL', 'SMS')),
    transport varchar(32) NOT NULL,
    intent_fingerprint varchar(44) NOT NULL,
    encrypted_payload text,
    expires_at timestamptz NOT NULL,
    state varchar(16) NOT NULL DEFAULT 'QUEUED'
        CHECK (state IN ('QUEUED', 'READY', 'PROCESSING', 'RETRY', 'BLOCKED', 'SUBMITTED', 'FAILED', 'CANCELLED', 'UNKNOWN')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    first_attempt_at timestamptz,
    retry_window_seconds integer NOT NULL CHECK (retry_window_seconds = 0 OR retry_window_seconds BETWEEN 300 AND 82800),
    next_attempt_at timestamptz NOT NULL,
    lease_token uuid,
    lease_until timestamptz,
    provider_id uuid,
    failure_code varchar(48),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (octet_length(encrypted_payload) <= 65536),
    CHECK ((state IN ('SUBMITTED', 'FAILED', 'CANCELLED', 'UNKNOWN')) = (encrypted_payload IS NULL)),
    CHECK ((state = 'PROCESSING') = (lease_token IS NOT NULL AND lease_until IS NOT NULL))
);

CREATE INDEX notification_due ON notification_messages (next_attempt_at, id)
    WHERE state IN ('READY', 'RETRY', 'BLOCKED');
CREATE INDEX notification_abandoned ON notification_messages (lease_until, id)
    WHERE state = 'PROCESSING';
CREATE INDEX notification_expiry ON notification_messages (expires_at)
    WHERE encrypted_payload IS NOT NULL;
