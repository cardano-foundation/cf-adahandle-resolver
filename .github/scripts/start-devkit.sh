#!/usr/bin/env bash
# Starts Yaci DevKit with its Yaci Store and waits until that store follows the devnet: it serves
# protocol parameters and its latest block keeps advancing past MIN_HEIGHT.
#
# The tests build transactions from the devkit's Yaci Store and its faucet waits on it. Now and
# then the store stops following the chain right after startup (stuck at block ~10 while the
# devnet keeps producing blocks), which otherwise surfaces minutes later as "the DevKit faucet did
# not fund ...". Then the devkit is stopped, its devnet wiped, and started once more.
set -euo pipefail

MIN_HEIGHT="${MIN_HEIGHT:-20}"
ATTEMPTS="${ATTEMPTS:-2}"

height() { curl -sf "${DEVNET_STORE_URL}blocks/latest" | python3 -c 'import json,sys; print(json.load(sys.stdin)["height"])' 2>/dev/null || echo 0; }

store_follows_devnet() {
  for _ in $(seq 1 150); do
    curl -sf "${DEVNET_STORE_URL}epochs/latest/parameters" > /dev/null && break
    sleep 2
  done
  local previous current
  previous=$(height)
  for _ in $(seq 1 24); do
    sleep 5
    current=$(height)
    echo "devkit Yaci Store at block $current"
    (( current >= MIN_HEIGHT && current > previous )) && return 0
    previous=$current
  done
  return 1
}

stop_devkit() {
  pkill -TERM -f yaci-cli || true
  sleep 10
  for p in yaci-store yano ogmios cardano-node cardano-submit-api; do pkill -KILL -f "$p" || true; done
  rm -rf ~/.yaci-cli/local-clusters
}

for attempt in $(seq 1 "$ATTEMPTS"); do
  nohup yaci-devkit up --enable-yaci-store >> "$RUNNER_TEMP/devkit.log" 2>&1 &
  if [ "$attempt" != 1 ] && store_follows_devnet; then  # TEMPORARY: force a restart
    echo "devkit Yaci Store follows the devnet (attempt $attempt)"
    exit 0
  fi
  echo "::warning::the devkit's Yaci Store doesn't follow the devnet (stuck at block $(height), attempt $attempt of $ATTEMPTS)"
  tail -n 60 ~/.yaci-cli/components/store/logs/*.log 2>/dev/null || true
  # Keep this attempt's store log for the artifact before the restart rotates it.
  cp -r ~/.yaci-cli/components/store/logs "$RUNNER_TEMP/devkit-store-logs-attempt-$attempt" 2>/dev/null || true
  stop_devkit
done
echo "::error::the devkit's Yaci Store doesn't follow the devnet after $ATTEMPTS attempts"
exit 1
