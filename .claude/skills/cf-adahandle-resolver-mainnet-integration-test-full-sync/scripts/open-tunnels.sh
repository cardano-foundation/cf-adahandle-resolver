#!/usr/bin/env bash
# Open background SSH tunnels:
#   localhost:$LOCAL_API_PORT -> remote:9095  (API)
#   localhost:$LOCAL_DB_PORT  -> remote:5432  (Postgres)
# Stores the PID in $SKILL_DIR/tunnels.pid so close-tunnels.sh can stop it.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

PID_FILE="$SKILL_DIR/tunnels.pid"

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "[tunnels] already running (pid $(cat "$PID_FILE")). Closing first."
  kill "$(cat "$PID_FILE")" || true
  sleep 1
fi
rm -f "$PID_FILE"

echo "[tunnels] opening: $LOCAL_API_PORT -> 9095 (api), $LOCAL_DB_PORT -> 5432 (db)"

# -N: no remote command, -T: no PTY, -f: background.
# ServerAliveInterval keeps it alive across NAT timeouts during long syncs.
ssh -o BatchMode=yes \
    -o ServerAliveInterval=30 -o ServerAliveCountMax=120 \
    -o ExitOnForwardFailure=yes \
    -NTf \
    -L "${LOCAL_API_PORT}:localhost:9095" \
    -L "${LOCAL_DB_PORT}:localhost:5432" \
    "$REMOTE"

pid=$(pgrep -af "ssh.*-L ${LOCAL_API_PORT}:localhost:9095.*${REMOTE}" | awk '{print $1}' | head -1)
if [[ -z "$pid" ]]; then
  echo "ERROR: failed to locate background SSH tunnel PID" >&2
  exit 1
fi
echo "$pid" > "$PID_FILE"
echo "[tunnels] up (pid $pid)"

echo "[tunnels] sanity-checking http://localhost:${LOCAL_API_PORT}/actuator/health ..."
sleep 2
if curl -fsS --max-time 5 "http://localhost:${LOCAL_API_PORT}/actuator/health" >/dev/null; then
  echo "[tunnels] /actuator/health reachable"
else
  echo "[tunnels] /actuator/health NOT yet reachable — fine if the API is still booting"
fi
