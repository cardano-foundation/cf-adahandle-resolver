#!/usr/bin/env bash
# Starts the resolver ($RUNNER_TEMP/resolver.jar) against the Yaci DevKit devnet and the PostgreSQL
# service of the integration-tests workflow, and waits until it is up. Each start logs to its own
# file; the PID goes to $RUNNER_TEMP/resolver.pid.
set -euo pipefail

log="$RUNNER_TEMP/resolver-$(date +%H%M%S).log"
SPRING_ACTIVE_PROFILES=devnet,postgres \
DB_URL=jdbc:postgresql://localhost:5432/adahandle \
DB_USERNAME=adahandle \
DB_PASSWORD=adahandle \
  nohup java -Xmx1g -jar "$RUNNER_TEMP/resolver.jar" > "$log" 2>&1 &
echo $! > "$RUNNER_TEMP/resolver.pid"

for i in $(seq 1 90); do
  if curl -sf "$API_BASE_URL/actuator/health" > /dev/null; then
    echo "Resolver is up (log: $log)"
    exit 0
  fi
  if ! kill -0 "$(cat "$RUNNER_TEMP/resolver.pid")" 2>/dev/null; then
    echo "Resolver exited"; tail -100 "$log"; exit 1
  fi
  sleep 2
done
echo "Resolver did not start within 3 minutes"; tail -100 "$log"; exit 1
