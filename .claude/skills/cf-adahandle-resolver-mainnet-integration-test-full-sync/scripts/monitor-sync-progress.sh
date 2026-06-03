#!/usr/bin/env bash
# Periodically print chain sync progress (cursor vs Koios tip, window-relative).
# Side-effect free: never hits /actuator/health, so it can't trigger TipFinder.
#
# Usage:
#   monitor-sync-progress.sh [interval_seconds] [--until-tip]
#
#   interval_seconds   how often to sample (default 300s / 5 min)
#   --until-tip        exit 0 the first time window progress >= 99.95%
#                      (otherwise loops forever; Ctrl-C to stop)

set -euo pipefail
HERE="$(dirname "${BASH_SOURCE[0]}")"
# shellcheck disable=SC1091
source "$HERE/load-config.sh" >/dev/null

INTERVAL="${1:-300}"
UNTIL_TIP=0
[[ "${2:-}" == "--until-tip" ]] && UNTIL_TIP=1

prev_slot=""
prev_epoch_time=""

while true; do
  cursor_row=$(ssh -o BatchMode=yes "$REMOTE" \
    "docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -tA -F '|' -c 'SELECT slot, block_number, era FROM cursor_ ORDER BY update_datetime DESC LIMIT 1;'" 2>/dev/null || echo "||")
  cursor_slot=$(echo "$cursor_row" | awk -F'|' '{print $1}')
  cursor_block=$(echo "$cursor_row" | awk -F'|' '{print $2}')
  cursor_era=$(echo "$cursor_row" | awk -F'|' '{print $3}')

  tip=$(curl -fsS --max-time 10 "https://api.koios.rest/api/v1/tip" 2>/dev/null || echo "[]")
  tip_slot=$(echo "$tip" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d[0]["abs_slot"] if d else 0)' 2>/dev/null || echo 0)

  if [[ -z "$cursor_slot" || "$tip_slot" == "0" ]]; then
    echo "[$(date '+%H:%M:%S')] (snapshot failed — DB or Koios unreachable; will retry)"
  else
    sync_start=${SYNC_START_SLOT:-47931310}
    total=$(( tip_slot - sync_start ))
    done_slots=$(( cursor_slot - sync_start ))
    pct=$(python3 -c "print(f'{100*$done_slots/$total:.2f}')")

    now_epoch=$(date +%s)
    if [[ -n "$prev_slot" ]]; then
      dslots=$(( cursor_slot - prev_slot ))
      dt=$(( now_epoch - prev_epoch_time ))
      if (( dslots > 0 && dt > 0 )); then
        slots_per_sec=$(python3 -c "print(f'{$dslots/$dt:.0f}')")
        left=$(( tip_slot - cursor_slot ))
        eta_min=$(python3 -c "print(f'{$left/($dslots/$dt)/60:.0f}')")
        echo "[$(date '+%H:%M:%S')] slot=$cursor_slot block=$cursor_block era=$cursor_era  ${pct}%  +${dslots} slots in ${dt}s (${slots_per_sec} sl/s)  ETA ~${eta_min}min"
      else
        echo "[$(date '+%H:%M:%S')] slot=$cursor_slot block=$cursor_block era=$cursor_era  ${pct}%  (no progress in ${dt}s)"
      fi
    else
      echo "[$(date '+%H:%M:%S')] slot=$cursor_slot block=$cursor_block era=$cursor_era  ${pct}%  (baseline sample)"
    fi
    prev_slot=$cursor_slot
    prev_epoch_time=$now_epoch

    if (( UNTIL_TIP )); then
      reached=$(python3 -c "print(1 if 100*$done_slots/$total >= 99.95 else 0)")
      if [[ "$reached" == "1" ]]; then
        echo "[monitor] window progress >= 99.95% — exiting (cursor era=$cursor_era)."
        exit 0
      fi
    fi
  fi
  sleep "$INTERVAL"
done
