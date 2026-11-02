#!/usr/bin/env bash
# Waits until the devkit's Yaci Store follows the devnet: its latest block keeps advancing past
# MIN_HEIGHT. The tests build transactions from it and the faucet waits on it, so a stalled store
# otherwise only shows up minutes later as "the DevKit faucet did not fund ...".
set -euo pipefail

MIN_HEIGHT="${MIN_HEIGHT:-20}"
height() { curl -sf "${DEVNET_STORE_URL}blocks/latest" | python3 -c 'import json,sys; print(json.load(sys.stdin)["height"])' 2>/dev/null || echo 0; }

previous=$(height)
for i in $(seq 1 24); do
  sleep 5
  current=$(height)
  echo "devkit Yaci Store at block $current"
  if (( current >= MIN_HEIGHT && current > previous )); then
    echo "devkit Yaci Store follows the devnet"
    exit 0
  fi
  previous=$current
done
echo "::error::the devkit's Yaci Store stopped following the devnet (stuck at block $(height))"
tail -n 100 ~/.yaci-cli/components/store/logs/*.log 2>/dev/null || true
exit 1
