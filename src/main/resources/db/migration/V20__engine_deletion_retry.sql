ALTER TABLE deletion_requests
    ADD COLUMN pending_subject_key VARCHAR(64),
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_deletion_requests_retry ON deletion_requests (next_attempt_at, id)
    WHERE pending_subject_key IS NOT NULL AND status = 'ENGINE_PENDING';
