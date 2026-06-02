#!/usr/bin/env bash
# Source this script; do not execute it.
# Loads config.env (if present) and applies CLI overrides via env variables
# the caller already exported. Exits with a clear error if required values
# are missing.

set -eo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "$SKILL_DIR/config.env" ]]; then
  # shellcheck disable=SC1091
  set -a
  source "$SKILL_DIR/config.env"
  set +a
fi

: "${REMOTE_PROJECT_DIR:=\$HOME/git/cf-adahandle-resolver}"
: "${BRANCH:=main}"
: "${GIT_REPO_URL:=https://github.com/cardano-foundation/cf-adahandle-resolver.git}"
: "${ENV_FILE:=.env}"
: "${LOCAL_API_PORT:=9095}"
: "${LOCAL_DB_PORT:=5432}"
: "${SAMPLE_N:=10}"
: "${EXPECTED_HANDLES:=}"

# Remote-side facts (match docker-compose.yml + .env in the project root).
: "${DB_CONTAINER:=cf-adahandle-resolver-db-1}"
: "${DB_NAME:=adahandle}"
: "${DB_USER:=cardano}"
: "${SYNC_START_SLOT:=47931310}"

if [[ -z "${REMOTE_SSH_HOST:-}" ]]; then
  echo "ERROR: required config missing: REMOTE_SSH_HOST" >&2
  echo "Fill it in $SKILL_DIR/config.env (copy from config.env.example) or export it before running." >&2
  return 1 2>/dev/null || exit 1
fi

# REMOTE_SSH_USER is OPTIONAL. If empty, connect by host only so ssh_config
# aliases take effect. If set, force user@host.
if [[ -n "${REMOTE_SSH_USER:-}" ]]; then
  REMOTE="${REMOTE_SSH_USER}@${REMOTE_SSH_HOST}"
else
  REMOTE="${REMOTE_SSH_HOST}"
fi

export REMOTE_SSH_HOST REMOTE_SSH_USER REMOTE_PROJECT_DIR BRANCH GIT_REPO_URL ENV_FILE
export LOCAL_API_PORT LOCAL_DB_PORT SAMPLE_N EXPECTED_HANDLES
export DB_CONTAINER DB_NAME DB_USER SYNC_START_SLOT
export SKILL_DIR REMOTE

echo "[config] remote = $REMOTE"
echo "[config] dir    = $REMOTE_PROJECT_DIR"
echo "[config] branch = $BRANCH"
echo "[config] env    = $ENV_FILE"
echo "[config] ports  = API:$LOCAL_API_PORT  DB:$LOCAL_DB_PORT"
