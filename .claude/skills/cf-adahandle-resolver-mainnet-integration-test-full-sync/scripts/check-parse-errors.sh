#!/usr/bin/env bash
# The core hard-fork regression check. Scans the api container logs for genuine
# block-parse failures and reports the cursor era.
#
# A "genuine" parse error is a BlockParseRuntimeException / "Block parsing error"
# whose root cause is a decode failure. We EXCLUDE the benign shutdown cascade
# (root cause "Database is already closed" / "context closed already" /
# "Scheduled to stop" / "Fetcher has already been stopped") — those are
# teardown/relay-wedge artifacts, not block-format incompatibilities.
#
# Exits 0 if there are zero genuine parse errors, 1 otherwise.
# Grepping happens on the remote so a multi-hour log isn't transferred whole.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

ssh_exec() { ssh -o BatchMode=yes "$REMOTE" "$@"; }

SIG='BlockParseRuntimeException|Block parsing error|Error in parsing|DeserializationException|CborException|CborRuntimeException'
BENIGN='already closed|context closed|Scheduled to stop|Fetcher has already been stopped'

echo "[parse-check] scanning api logs on $REMOTE ..."
# Pull only candidate error lines (matching SIG) from the remote.
candidates=$(ssh_exec "cd $REMOTE_PROJECT_DIR && docker compose --env-file $ENV_FILE logs --no-color --no-log-prefix api 2>/dev/null | grep -E '$SIG' || true")

total=$(printf '%s\n' "$candidates" | grep -cE "$SIG" || true)
genuine=$(printf '%s\n' "$candidates" | grep -E "$SIG" | grep -vE "$BENIGN" || true)
genuine_count=$(printf '%s\n' "$genuine" | grep -cE "$SIG" || true)
[[ -z "$candidates" ]] && total=0
[[ -z "$genuine" ]] && genuine_count=0

cursor_era=$(ssh_exec "docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -tA -c 'SELECT era FROM cursor_ ORDER BY update_datetime DESC LIMIT 1;'" 2>/dev/null || echo "?")

# yaci-store stores the cursor era as a numeric ordinal:
# 1=Byron 2=Shelley 3=Allegra 4=Mary 5=Alonzo 6=Babbage 7=Conway.
# (Some builds may store the name instead, so accept both.)
case "$cursor_era" in
  7|Conway) era_name="Conway"; conway_reached=1 ;;
  6|Babbage) era_name="Babbage"; conway_reached=0 ;;
  5|Alonzo) era_name="Alonzo"; conway_reached=0 ;;
  *) era_name="era=$cursor_era"; conway_reached=0 ;;
esac

echo "[parse-check] candidate parse-error lines:        $total"
echo "[parse-check] genuine (excluding shutdown noise): $genuine_count"
echo "[parse-check] cursor era at tip:                  $era_name ($cursor_era)"

if [[ "$genuine_count" -gt 0 ]]; then
  echo
  echo "[parse-check] FAIL — genuine block-parse error(s). First 10:" >&2
  printf '%s\n' "$genuine" | head -10 >&2
  exit 1
fi

if [[ "$conway_reached" -ne 1 ]]; then
  echo
  echo "[parse-check] NOTE: cursor era is $era_name ($cursor_era), not yet Conway (7) — the sync has not crossed into the Conway/Van Rossem window. Keep syncing before declaring hard-fork readiness." >&2
fi

echo "[parse-check] OK — no genuine block-parse errors."
