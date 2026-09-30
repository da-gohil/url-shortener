-- Who changed which link or account, and when. The actor's email is copied in so an
-- entry still reads correctly if the account later changes or goes away.
CREATE TABLE audit_events
(
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    actor_email VARCHAR(255) NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    target_type VARCHAR(20)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    summary     TEXT         NOT NULL
);

CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at DESC);
