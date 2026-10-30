#!/usr/bin/env bash
# Smoke-tests a native image container. Fails on any GraalVM missing-metadata error.
#
#   .github/scripts/native-smoke-test.sh <image> <api|postgres|sync>
#
#   api       H2 in memory, indexer off: the REST endpoints answer.
#   postgres  PostgreSQL at localhost:5432 (user/password/db: adahandle), indexer off: all
#             Flyway migrations are applied, including those of every yaci-store jar.
#   sync      H2, indexer on: syncs mainnet for SYNC_SECONDS and checks the cursor advanced.

set -euo pipefail

IMAGE="$1"
MODE="$2"
PORT="${PORT:-9095}"
SYNC_SECONDS="${SYNC_SECONDS:-180}"
START_SLOT=47931310
NAME="native-smoke-$MODE"

run() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  docker run -d --name "$NAME" --network host -e PORT="$PORT" "$@" "$IMAGE" >/dev/null
}

wait_for_api() {
  for _ in $(seq 1 120); do
    if curl -sf -o /dev/null "http://localhost:$PORT/apidocs"; then
      return 0
    fi
    if [[ "$(docker inspect -f '{{.State.Running}}' "$NAME")" != "true" ]]; then
      echo "container exited during startup" >&2
      return 1
    fi
    sleep 1
  done
  echo "API not up after 120s" >&2
  return 1
}

expect_status() {
  local path="$1" expected="$2" actual
  actual=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT$path")
  echo "  $path -> $actual (expected $expected)"
  [[ "$actual" == "$expected" ]]
}

finish() {
  local rc=$?
  echo "--- container log (tail) ---"
  docker logs --tail 80 "$NAME" 2>&1 || true
  if docker logs "$NAME" 2>&1 | grep -E 'Missing(Reflection|Resource|JNI|Serialization|Proxy)[A-Za-z]*Error|UnsupportedFeatureError'; then
    echo "FAIL: missing native-image metadata" >&2
    rc=1
  fi
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  [[ $rc -eq 0 ]] && echo "PASS: $MODE" || echo "FAIL: $MODE" >&2
  exit $rc
}
trap finish EXIT

case "$MODE" in
  api)
    run -e SPRING_ACTIVE_PROFILES=mainnet,h2,disable-indexer -e DB_URL=jdbc:h2:mem:smoke
    wait_for_api
    expect_status /apidocs 200
    expect_status /swagger-ui/index.html 200
    expect_status '/api/v1/addresses/by-ada-handle/$nobody' 404
    expect_status /api/v1/ada-handles/by-stake-address/stake1nobody 200
    expect_status /api/v1/ada-handles/by-payment-address/addr1nobody 200
    expect_status /api/v1/sync/status 200
    ;;
  postgres)
    run -e SPRING_ACTIVE_PROFILES=mainnet,postgres,disable-indexer \
        -e DB_URL=jdbc:postgresql://localhost:5432/adahandle -e DB_USERNAME=adahandle -e DB_PASSWORD=adahandle
    wait_for_api
    expect_status /apidocs 200
    expect_status '/api/v1/addresses/by-ada-handle/$nobody' 404
    # yaci-store-core and yaci-store-utxo both ship db/store/postgresql; all five must run.
    docker logs "$NAME" 2>&1 | grep -E 'Successfully applied 5 migrations|Schema .* is up to date'
    ;;
  sync)
    run -e SPRING_ACTIVE_PROFILES=mainnet,h2 -e DB_URL=jdbc:h2:/tmp/smoke-db
    wait_for_api
    echo "  syncing mainnet for ${SYNC_SECONDS}s..."
    sleep "$SYNC_SECONDS"
    slot=$(curl -s "http://localhost:$PORT/api/v1/sync/status" | sed -E 's/.*"slot":([0-9]+).*/\1/')
    echo "  cursor slot: $slot (start $START_SLOT)"
    [[ "$slot" =~ ^[0-9]+$ ]] && (( slot > START_SLOT ))
    ;;
  *)
    echo "unknown mode: $MODE" >&2
    exit 2
    ;;
esac
