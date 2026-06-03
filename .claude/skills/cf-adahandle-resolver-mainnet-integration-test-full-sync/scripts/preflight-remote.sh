#!/usr/bin/env bash
# Read-only inspection of the remote box. Exits 0 if it's safe to proceed,
# 2 if there's an existing instance / occupied ports (caller must ask the
# user how to proceed), 1 on transport / SSH failure.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

ssh_exec() { ssh -o BatchMode=yes -o ConnectTimeout=10 "$REMOTE" "$@"; }

echo "[preflight] testing SSH transport..."
if ! ssh_exec true; then
  echo "ERROR: cannot SSH to $REMOTE (BatchMode). Check key auth and host reachability." >&2
  exit 1
fi

problems=0

echo "[preflight] looking for running docker compose stack in $REMOTE_PROJECT_DIR..."
running=$(ssh_exec "test -d $REMOTE_PROJECT_DIR && cd $REMOTE_PROJECT_DIR && docker compose ps --status running --format '{{.Service}}' 2>/dev/null || true")
if [[ -n "$running" ]]; then
  echo "  FOUND running services: $running"
  problems=$((problems + 1))
else
  echo "  none"
fi

echo "[preflight] checking remote-side ports 9095 (API) and 5432 (postgres)..."
ports=$(ssh_exec "ss -ltnH 2>/dev/null | awk '\$4 ~ /:(9095|5432)\$/ {print \$4}'") || true
if [[ -n "$ports" ]]; then
  echo "  FOUND listeners: $ports"
  problems=$((problems + 1))
else
  echo "  none"
fi

echo "[preflight] checking for a populated adahandle-db volume..."
vol=$(ssh_exec "docker volume ls --format '{{.Name}}' 2>/dev/null | grep -E 'adahandle-db$' || true")
if [[ -n "$vol" ]]; then
  echo "  FOUND volume(s): $vol  (a fresh sync needs this empty — confirm wipe vs resume)"
  problems=$((problems + 1))
else
  echo "  none"
fi

echo "[preflight] checking docker daemon reachable on $REMOTE..."
if ! ssh_exec "docker info >/dev/null 2>&1"; then
  echo "  WARNING: docker info failed — user may lack permissions or daemon is down" >&2
  problems=$((problems + 1))
else
  echo "  ok"
fi

echo "[preflight] checking whether $REMOTE_PROJECT_DIR/.git exists (informational)..."
if ssh_exec "test -d $REMOTE_PROJECT_DIR/.git"; then
  echo "  yes — repo already checked out; prepare-remote.sh will git reset & checkout $BRANCH"
else
  echo "  no — prepare-remote.sh will clone fresh"
fi

if (( problems > 0 )); then
  echo
  echo "[preflight] $problems problem(s) found. STOPPING. Ask the user whether to clean up or abort." >&2
  exit 2
fi

echo "[preflight] OK — safe to proceed."
