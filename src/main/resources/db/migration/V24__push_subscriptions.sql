CREATE TABLE push_subscriptions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    endpoint VARCHAR(2048) NOT NULL UNIQUE,
    p256dh VARCHAR(128) NOT NULL,
    auth VARCHAR(32) NOT NULL,
    last_attempt_date DATE
);

CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id);
