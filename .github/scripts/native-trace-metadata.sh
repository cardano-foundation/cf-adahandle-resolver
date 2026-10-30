#!/usr/bin/env bash
# Regenerates the GraalVM reachability metadata for the native image by tracing the app on the
# JVM (in AOT mode, as the native image runs it) with the native-image agent:
#   1. API only (H2, indexer off), hitting every endpoint;
#   2. a short mainnet sync (H2), covering yaci networking, block parsing and the handle processor.
#
# Run with JAVA_HOME pointing at GraalVM for JDK 25, from the repository root:
#   .github/scripts/native-trace-metadata.sh [sync-seconds]
#
# Then rebuild (./mvnw -Pnative -DskipTests native:compile) and run the smoke tests.

set -euo pipefail

SYNC_SECONDS="${1:-180}"
PORT=19097
OUT=src/main/resources/META-INF/native-image/org.cardanofoundation/cf-adahandle-resolver
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

"$JAVA_HOME/bin/java" -version 2>&1 | grep -q GraalVM || { echo "JAVA_HOME must be GraalVM" >&2; exit 1; }

./mvnw -B -q -Pnative -DskipTests clean package
JAR=$(ls target/cf-adahandle-resolver-*.jar | grep -v original | head -1)
mkdir -p "$WORK/agent"

trace() {
  "$JAVA_HOME/bin/java" -Dspring.aot.enabled=true \
    -agentlib:native-image-agent=config-merge-dir="$WORK/agent" -jar "$JAR" > "$WORK/$1.log" 2>&1 &
  PID=$!
  for _ in $(seq 1 120); do
    curl -sf -o /dev/null "http://localhost:$PORT/apidocs" && return 0
    kill -0 $PID 2>/dev/null || { cat "$WORK/$1.log" >&2; return 1; }
    sleep 1
  done
  return 1
}

stop() {
  kill $PID
  wait $PID 2>/dev/null || true
}

echo "[trace] API"
SPRING_ACTIVE_PROFILES=mainnet,h2,disable-indexer DB_URL=jdbc:h2:mem:trace PORT=$PORT trace api
for path in /apidocs /apidocs.yaml /apidocs/swagger-config /swagger-ui.html /swagger-ui/index.html \
    /swagger-ui/swagger-initializer.js /swagger-ui/swagger-ui-bundle.js \
    '/api/v1/addresses/by-ada-handle/$nobody' /api/v1/ada-handles/by-stake-address/stake1nobody \
    /api/v1/ada-handles/by-payment-address/addr1nobody /api/v1/sync/status /api/v1/genesis /nonexistent; do
  curl -sL -o /dev/null "http://localhost:$PORT$path"
done
stop

echo "[trace] mainnet sync for ${SYNC_SECONDS}s"
SPRING_ACTIVE_PROFILES=mainnet,h2 DB_URL="jdbc:h2:$WORK/db" PORT=$PORT trace sync
sleep "$SYNC_SECONDS"
curl -s "http://localhost:$PORT/api/v1/sync/status"; echo
stop

mkdir -p "$OUT"
python3 - "$WORK/agent/reachability-metadata.json" "$OUT/reachability-metadata.json" <<'EOF'
import json, sys

def drop(entry):
    # Hibernate deliberately keeps its ByteBuddy BytecodeProvider out of native images (it would
    # define classes at runtime) and falls back to its no-op provider; the JVM trace sees it anyway.
    text = json.dumps(entry)
    return 'org.hibernate.bytecode' in text and ('bytebuddy' in text.lower() or 'META-INF/services' in text)

metadata = json.load(open(sys.argv[1]))
for key, entries in metadata.items():
    if isinstance(entries, list):
        metadata[key] = [e for e in entries if not drop(e)]
with open(sys.argv[2], 'w') as f:
    json.dump(metadata, f, indent=2, sort_keys=True)
    f.write('\n')
print({k: len(v) for k, v in metadata.items() if isinstance(v, list)})
EOF
echo "[trace] wrote $OUT/reachability-metadata.json"
