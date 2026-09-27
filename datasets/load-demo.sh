#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
curl -fsS -X POST "$BASE_URL/api/v1/payments" -H 'Content-Type: application/json' -H 'Idempotency-Key: dataset-payment-001' -d '{"merchantReference":"dataset-order-001","amount":49.90,"currency":"PEN"}'
echo
curl -fsS -X POST "$BASE_URL/api/v1/payments" -H 'Content-Type: application/json' -H 'Idempotency-Key: dataset-payment-002' -d '{"merchantReference":"dataset-order-retry","amount":13.37,"currency":"PEN"}'
echo
