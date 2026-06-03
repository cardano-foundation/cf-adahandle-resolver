#!/usr/bin/env bash
# Report chain sync progress WITHOUT touching /actuator/health (which triggers
# a TipFinder node connection and would add to the connection storm if the
# dead-relay-IP issue is live).
#
# Reads the cursor straight from Postgres on the remote, and pulls the current
# mainnet tip from Koios (free, no key). Window-relative percent + rough ETA.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

cursor_row=$(ssh -o BatchMode=yes "$REMOTE" \
  "docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -tA -F '|' -c 'SELECT slot, block_number, era, update_datetime FROM cursor_ ORDER BY update_datetime DESC LIMIT 1;'")

cursor_slot=$(echo "$cursor_row" | awk -F'|' '{print $1}')
cursor_block=$(echo "$cursor_row" | awk -F'|' '{print $2}')
cursor_era=$(echo "$cursor_row" | awk -F'|' '{print $3}')
cursor_time=$(echo "$cursor_row" | awk -F'|' '{print $4}')

tip=$(curl -fsS --max-time 10 "https://api.koios.rest/api/v1/tip")
tip_slot=$(echo "$tip" | python3 -c 'import json,sys;print(json.load(sys.stdin)[0]["abs_slot"])')
tip_block=$(echo "$tip" | python3 -c 'import json,sys;print(json.load(sys.stdin)[0]["block_no"])')
tip_epoch=$(echo "$tip" | python3 -c 'import json,sys;print(json.load(sys.stdin)[0]["epoch_no"])')

# Window-relative to the configured sync-start slot (first ADA Handle mint),
# so the percentage is meaningful and self-correcting throughout the run.
sync_start="${SYNC_START_SLOT:-47931310}"
slots_total=$(( tip_slot - sync_start ))
slots_done=$(( cursor_slot - sync_start ))
slots_left=$(( tip_slot - cursor_slot ))
pct=$(python3 -c "print(f'{100*$slots_done/$slots_total:.2f}')")

echo "[sync-progress]"
echo "  cursor:  slot=$cursor_slot  block=$cursor_block  era=$cursor_era  updated=$cursor_time"
echo "  tip:     slot=$tip_slot  block=$tip_block  epoch=$tip_epoch  (Koios mainnet)"
echo "  window:  start=$sync_start  span=$slots_total slots"
echo "  done:    $slots_done slots  (${pct}%)"
echo "  left:    $slots_left slots"
