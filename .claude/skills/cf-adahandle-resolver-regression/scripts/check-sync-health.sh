#!/usr/bin/env bash
# The indexer is at the tip and following it, in the expected era, with no block-parse errors.
# Also summarises ERROR log lines and rollback activity of the running container.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

MAX_LAG_SLOTS="${MAX_LAG_SLOTS:-600}"        # 10 minutes of slots
MAX_CURSOR_AGE_SEC="${MAX_CURSOR_AGE_SEC:-600}"
EXPECTED_ERA="${EXPECTED_ERA:-7}"            # 7 = Conway

echo "[sync-health]"
IFS='|' read -r slot block era age < <(sql "SELECT slot, block_number, era, extract(epoch FROM now() - update_datetime)::int FROM cursor_ ORDER BY update_datetime DESC LIMIT 1;")
tip=$(curl -s --max-time 20 https://api.koios.rest/api/v1/tip | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["abs_slot"])' 2>/dev/null || echo "")
info "cursor slot=$slot block=$block era=$era, updated ${age}s ago; Koios tip slot=${tip:-?}"

if [[ -n "$tip" ]] && (( tip - slot <= MAX_LAG_SLOTS )); then
  pass "at the tip ($(( tip - slot )) slots behind, limit $MAX_LAG_SLOTS)"
elif [[ -z "$tip" ]]; then
  info "Koios tip unavailable; skipped the lag check"
else
  fail "$(( tip - slot )) slots behind the tip (limit $MAX_LAG_SLOTS)"
fi
(( age <= MAX_CURSOR_AGE_SEC )) && pass "cursor is advancing (last update ${age}s ago)" \
  || fail "cursor not updated for ${age}s (limit ${MAX_CURSOR_AGE_SEC}s) - see the dead-relay issue in the full-sync skill"
[[ "$era" == "$EXPECTED_ERA" ]] && pass "era $era" || fail "era $era, expected $EXPECTED_ERA"

if bash "$SYNC_SKILL_DIR/scripts/check-parse-errors.sh" < /dev/null >/dev/null 2>&1; then
  pass "no block-parse errors in the api logs"
else
  fail "block-parse errors found: run $SYNC_SKILL_DIR/scripts/check-parse-errors.sh for details"
fi

# ERROR lines over the container's lifetime (docker logs survive restarts). Known upstream noise
# is reported, not failed.
ssh_exec "docker logs $API_CONTAINER 2>&1 | grep ' ERROR ' | sed -E 's/^.*ERROR [0-9]+ --- \[[^]]*\] *//' | cut -c1-120 | sort | uniq -c | sort -rn" \
  | while read -r count message; do
      case "$message" in
        *"Error stopping blockRangeSync"*)
          known "$count x '$message' (yaci-helper 0.4.7 NPE in BlockRangeSync.stop() during catch-up after a restart; harmless)" ;;
        *)
          info "$count x ERROR '$message'" ;;
      esac
    done

ssh_exec "docker logs $API_CONTAINER 2>&1 | grep -E 'Publishing rollback event|Rollback to slot .* deleted' " \
  | python3 -I -c '
import re, sys
restart = node = repaired = rows = 0
for line in sys.stdin:
    if "Publishing rollback event" in line:
        if "after restart" in line: restart += 1
        else: node += 1
    m = re.search(r"deleted (\d+) history rows", line)
    if m and int(m.group(1)) > 0:
        repaired += 1; rows += int(m.group(1))
print(f"  INFO   rollbacks over the container lifetime: {node} from the node, {restart} on restart; {repaired} repaired handles ({rows} history rows)")'

exit $FAILED
