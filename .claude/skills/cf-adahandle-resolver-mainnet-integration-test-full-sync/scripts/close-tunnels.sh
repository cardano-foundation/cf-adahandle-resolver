#!/usr/bin/env bash
# Tear down the background SSH tunnel created by open-tunnels.sh.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

PID_FILE="$SKILL_DIR/tunnels.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "[tunnels] no PID file — nothing to do."
  exit 0
fi

pid=$(cat "$PID_FILE")
if kill -0 "$pid" 2>/dev/null; then
  kill "$pid"
  echo "[tunnels] killed pid $pid"
else
  echo "[tunnels] pid $pid already gone"
fi
rm -f "$PID_FILE"
