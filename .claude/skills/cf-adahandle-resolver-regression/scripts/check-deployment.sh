#!/usr/bin/env bash
# What is under test: deployed commit vs. the branch head, image build time, Java and key
# library versions inside the running container. Informational; fails only if the stack is down.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

echo "[deployment]"
deployed=$(ssh_exec "cd $REMOTE_PROJECT_DIR && git rev-parse --short HEAD && git rev-parse --abbrev-ref HEAD" | paste -sd' ' -)
head=$(git ls-remote "$GIT_REPO_URL" "refs/heads/$BRANCH" | cut -c1-7)
info "deployed checkout: $deployed (origin/$BRANCH head: ${head:-?})"

if ! ssh_exec "docker inspect -f '{{.State.Running}}' $API_CONTAINER" 2>/dev/null | grep -q true; then
  fail "$API_CONTAINER is not running"
  exit 1
fi

ssh_exec "c=$API_CONTAINER
  echo \"image built: \$(docker inspect -f '{{.Created}}' \$(docker inspect -f '{{.Image}}' \$c))\"
  echo \"container up since: \$(docker inspect -f '{{.State.StartedAt}}' \$c)\"
  docker exec \$c java -version 2>&1 | grep -v JAVA_TOOL_OPTIONS | head -1
  docker exec \$c sh -c 'grep PRETTY_NAME /etc/os-release' 2>/dev/null
  d=\$(mktemp -d) && docker cp -q \$c:/app/cf-adahandle-resolver.jar \$d/app.jar 2>/dev/null &&
    unzip -Z1 \$d/app.jar | sed 's|.*/||' | grep -E '^(spring-boot|spring-core|yaci-store-core|yaci-core|flyway-core|hibernate-core|postgresql|springdoc-openapi-starter-webmvc-api)-[0-9][^/ ]*[.]jar$' | sort -u | paste -sd' ' -
  rm -rf \$d" | while IFS= read -r line; do info "$line"; done

pass "stack is running"
exit $FAILED
