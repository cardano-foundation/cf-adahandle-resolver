#!/usr/bin/env bash
# REST surface: OpenAPI spec, Swagger UI, sync status, and the resolver endpoints' error contract.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

echo "[api] $API"
expect() {  # expect <path> <status> <description>
  local status
  status=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$API$1")
  [[ "$status" == "$2" ]] && pass "$3 ($1 -> $status)" || fail "$3 ($1 -> $status, expected $2)"
}

expect /apidocs 200 "OpenAPI spec"
expect /swagger-ui/index.html 200 "Swagger UI"
expect /api/v1/sync/status 200 "sync status"
expect '/api/v1/addresses/by-ada-handle/%24definitely-not-a-handle-0000' 404 "unknown handle is 404"
expect /api/v1/ada-handles/by-payment-address/addr1nobody 200 "unknown payment address is an empty list"

paths=$(curl -s --max-time 20 "$API/apidocs" | python3 -I -c 'import sys,json; print(" ".join(sorted(json.load(sys.stdin)["paths"])))')
for p in /api/v1/addresses/by-ada-handle/{adaHandle} /api/v1/ada-handles/by-stake-address/{stakeAddress} \
         /api/v1/ada-handles/by-payment-address/{paymentAddress}; do
  [[ " $paths " == *" $p "* ]] && pass "spec documents $p" || fail "spec is missing $p"
done

exit $FAILED
