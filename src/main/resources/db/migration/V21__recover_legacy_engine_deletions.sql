UPDATE deletion_requests AS request
SET pending_subject_key = account.subject_key, next_attempt_at = CURRENT_TIMESTAMP
FROM users AS account
WHERE request.status = 'ENGINE_PENDING'
  AND request.pending_subject_key IS NULL
  AND request.subject_hash = encode(sha256(convert_to(account.subject_key, 'UTF8')), 'hex');
