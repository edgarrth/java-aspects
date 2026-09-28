ALTER TABLE idempotency_records
  ADD COLUMN request_hash VARCHAR(64);
