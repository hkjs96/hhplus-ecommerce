#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_ID="${USER_ID:-1}"
AMOUNT="${AMOUNT:-5000000}"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-loadtest-balance-$(date +%s)}"

echo "Charging user ${USER_ID} balance with ${AMOUNT} won..."

curl -sSf -X POST "${BASE_URL}/api/users/${USER_ID}/balance/charge" \
  -H 'Content-Type: application/json' \
  -d "$(cat <<EOF
{
  "amount": ${AMOUNT},
  "idempotencyKey": "${IDEMPOTENCY_KEY}"
}
EOF
)"

echo
echo "Balance charge request sent."
