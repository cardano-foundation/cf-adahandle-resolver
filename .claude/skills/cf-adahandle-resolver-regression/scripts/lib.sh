#!/usr/bin/env bash
# Shared helpers for the regression checks. Source this; do not execute it.
#
# Reuses the full-sync skill's config (config.env + load-config.sh): same host, remote dir,
# branch and tunnel ports, so the regression suite runs against the instance that skill deployed.

REG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SYNC_SKILL_DIR="$(cd "$REG_DIR/../cf-adahandle-resolver-mainnet-integration-test-full-sync" && pwd)"

# shellcheck disable=SC1091
source "$SYNC_SKILL_DIR/scripts/load-config.sh" >/dev/null

API="http://localhost:${LOCAL_API_PORT}"
API_CONTAINER="${API_CONTAINER:-cf-adahandle-resolver-api-1}"
# Kora Labs' public Handles API, the reference for who owns a handle.
ORACLE_API="${ORACLE_API:-https://api.handle.me}"
# api.handle.me rejects requests with a default client User-Agent (HTTP 403).
USER_AGENT="cf-adahandle-resolver-regression/1.0"
export API API_CONTAINER ORACLE_API USER_AGENT REG_DIR SYNC_SKILL_DIR

ssh_exec() { ssh -o BatchMode=yes -o ConnectTimeout=15 "$REMOTE" "$@"; }

# Runs one SQL statement on the remote Postgres; rows are '|'-separated. The statement goes
# over stdin, so it needs no shell quoting.
sql() {
  printf '%s\n' "$1" | ssh_exec "timeout ${SQL_TIMEOUT:-300} docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -tA -F'|' -v ON_ERROR_STOP=1"
}

pass()  { echo "  PASS   $*"; }
fail()  { echo "  FAIL   $*"; FAILED=1; }
known() { echo "  KNOWN  $*"; }
info()  { echo "  INFO   $*"; }

FAILED=0
