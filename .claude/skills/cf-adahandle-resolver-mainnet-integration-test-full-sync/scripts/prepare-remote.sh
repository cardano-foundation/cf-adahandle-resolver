#!/usr/bin/env bash
# Clone or reset $REMOTE_PROJECT_DIR on the remote and check out $BRANCH.
# Idempotent. Prints the resolved commit SHA at the end.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

echo "[prepare] target: $REMOTE:$REMOTE_PROJECT_DIR @ $BRANCH"

# Send the body as a heredoc with a quoted delimiter so nothing expands
# locally. Pass the dynamic values as positional args. The remote shell uses
# `eval` once to expand `$HOME` inside REMOTE_PROJECT_DIR.
ssh -o BatchMode=yes "$REMOTE" bash -s -- "$REMOTE_PROJECT_DIR" "$BRANCH" "$GIT_REPO_URL" <<'REMOTE_SCRIPT'
set -euo pipefail
eval "DIR=$1"
BRANCH="$2"
REPO="$3"

# Ensure github.com host key is trusted (idempotent), in case the repo URL is SSH.
mkdir -p "$HOME/.ssh"; touch "$HOME/.ssh/known_hosts"
chmod 700 "$HOME/.ssh"; chmod 600 "$HOME/.ssh/known_hosts"
if ! ssh-keygen -F github.com -f "$HOME/.ssh/known_hosts" >/dev/null 2>&1; then
  ssh-keyscan -H github.com 2>/dev/null >> "$HOME/.ssh/known_hosts"
  echo "[remote] added github.com to known_hosts"
fi

if [ -d "$DIR/.git" ]; then
  echo "[remote] existing repo found at $DIR, resetting..."
  cd "$DIR"
  git fetch --all --prune
  git reset --hard
  git clean -fdx
  git checkout "$BRANCH"
  git pull --ff-only origin "$BRANCH"
else
  echo "[remote] cloning fresh into $DIR"
  mkdir -p "$(dirname "$DIR")"
  git clone "$REPO" "$DIR"
  cd "$DIR"
  git checkout "$BRANCH"
fi

SHA=$(git rev-parse --short HEAD)
SUBJECT=$(git log -1 --pretty=%s)
echo "[remote] HEAD = $SHA  ($SUBJECT)"
REMOTE_SCRIPT
