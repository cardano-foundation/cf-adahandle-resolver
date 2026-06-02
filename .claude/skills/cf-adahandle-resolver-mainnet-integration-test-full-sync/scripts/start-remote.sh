#!/usr/bin/env bash
# Bring up the docker compose stack on the remote. Builds the API image from
# the checked-out branch source (--pull refreshes the base image) so we test
# THIS branch's code, not a published :latest.
#
# Refuses to auto-wipe a populated adahandle-db volume — surfaces it instead.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

ssh_exec() { ssh -o BatchMode=yes "$REMOTE" "$@"; }

echo "[start] verifying $ENV_FILE present in $REMOTE_PROJECT_DIR ..."
if ! ssh_exec "test -f $REMOTE_PROJECT_DIR/$ENV_FILE"; then
  echo "ERROR: $REMOTE_PROJECT_DIR/$ENV_FILE missing on remote." >&2
  exit 1
fi

echo "[start] checking the adahandle-db volume isn't already populated ..."
populated=$(ssh_exec "docker run --rm -v adahandle-db:/v alpine sh -c 'ls -A /v 2>/dev/null | head -1' 2>/dev/null || true")
if [[ -n "$populated" ]]; then
  echo "ERROR: the adahandle-db volume already has data. A fresh sync needs it empty." >&2
  echo "       Decide with the user: resume (just 'up -d') or wipe ('docker compose --env-file $ENV_FILE down -v')." >&2
  exit 2
fi

echo "[start] building API image from source (refreshing base image)..."
ssh_exec "cd $REMOTE_PROJECT_DIR && docker compose --env-file $ENV_FILE build --pull api"

echo "[start] docker compose up -d ..."
ssh_exec "cd $REMOTE_PROJECT_DIR && docker compose --env-file $ENV_FILE up -d"

echo "[start] running containers:"
ssh_exec "cd $REMOTE_PROJECT_DIR && docker compose --env-file $ENV_FILE ps"
