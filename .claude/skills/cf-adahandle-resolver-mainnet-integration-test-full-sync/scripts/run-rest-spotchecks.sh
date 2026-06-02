#!/usr/bin/env bash
# REST regression spot-checks against the tunneled API.
#
# Samples SAMPLE_N handles from the synced ada_handle table on the remote DB,
# then for each asserts (against http://localhost:$LOCAL_API_PORT):
#   forward: GET /api/v1/addresses/by-ada-handle/{handle}
#            -> stakeAddress/paymentAddress equal the stored row
#   reverse: GET /api/v1/ada-handles/by-stake-address/{stakeAddress}
#            -> the handle is present in the returned list
#
# This is a DB-vs-API consistency + round-trip check: it can't yield false
# failures from "handle doesn't exist" or on-chain drift. Assumes the sync is
# at (or near) tip and the tunnels are open.
#
# Exits 0 only if every sampled handle passes both directions.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

echo "[spotcheck] sampling up to $SAMPLE_N handles from $DB_NAME.ada_handle on $REMOTE ..."
# Emit a JSON array so handle names containing any UTF-8 (including spaces or the
# pipe char — CIP-68 names are arbitrary) parse unambiguously. Skip empty names.
samples=$(ssh -o BatchMode=yes "$REMOTE" \
  "docker exec $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -tA -c \"SELECT coalesce(json_agg(row_to_json(t)),'[]') FROM (SELECT name, stake_address AS stake, payment_address AS payment FROM ada_handle WHERE name <> '' AND stake_address IS NOT NULL AND payment_address IS NOT NULL ORDER BY name LIMIT $SAMPLE_N) t;\"" 2>/dev/null || true)

if [[ -z "$samples" || "$samples" == "[]" ]]; then
  echo "ERROR: no usable handles found in ada_handle — is the sync populated yet?" >&2
  exit 1
fi

# Write the sampled rows to a temp file and pass its path as argv[1]. (Do NOT
# pipe data to `python3 -`: the heredoc is consumed as the program from stdin,
# so a piped stdin would be empty.)
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
printf '%s\n' "$samples" > "$tmp"

set +e
API="http://localhost:${LOCAL_API_PORT}" python3 - "$tmp" <<'PY'
import sys, os, json, urllib.parse, urllib.request

api = os.environ["API"]

def get(path):
    req = urllib.request.Request(api + path)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, None

with open(sys.argv[1]) as fh:
    rows = json.load(fh)

total = passed = 0
fails = []
for row in rows:
    name = row.get("name")
    stake = row.get("stake")
    payment = row.get("payment")
    if not name:
        continue
    total += 1
    msgs = []

    enc = urllib.parse.quote(name, safe="")
    st, body = get(f"/api/v1/addresses/by-ada-handle/{enc}")
    if st != 200 or not body:
        msgs.append(f"forward HTTP {st}")
    else:
        if body.get("stakeAddress") != stake or body.get("paymentAddress") != payment:
            msgs.append(f"forward mismatch: api=({body.get('stakeAddress')},{body.get('paymentAddress')}) db=({stake},{payment})")

    st, arr = get(f"/api/v1/ada-handles/by-stake-address/{urllib.parse.quote(stake, safe='')}")
    if st != 200 or arr is None:
        msgs.append(f"reverse HTTP {st}")
    elif name not in arr:
        msgs.append(f"reverse missing: '{name}' not in {arr}")

    if msgs:
        fails.append((name, msgs))
    else:
        passed += 1

print(f"[spotcheck] {passed}/{total} handles passed forward+reverse round-trip")
for n, m in fails[:10]:
    print(f"  FAIL {n}: {'; '.join(m)}")

sys.exit(0 if (total > 0 and passed == total) else 1)
PY
rc=$?
set -e
exit $rc
