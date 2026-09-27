CREATE TABLE payments (
  id UUID PRIMARY KEY,
  merchant_reference VARCHAR(120) NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  currency CHAR(3) NOT NULL,
  status VARCHAR(30) NOT NULL,
  gateway_reference VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payments_merchant_reference ON payments(merchant_reference);
CREATE TABLE audit_events (
  id UUID PRIMARY KEY,
  action VARCHAR(80) NOT NULL,
  method VARCHAR(200) NOT NULL,
  outcome VARCHAR(20) NOT NULL,
  error_type VARCHAR(120),
  occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at);
CREATE TABLE idempotency_records (
  idempotency_key VARCHAR(120) PRIMARY KEY,
  status VARCHAR(20) NOT NULL,
  response_json TEXT,
  updated_at TIMESTAMPTZ NOT NULL
);