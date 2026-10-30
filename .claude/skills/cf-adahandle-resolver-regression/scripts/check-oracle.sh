#!/usr/bin/env bash
# Compares a random sample of handles with Kora Labs' Handles API (api.handle.me), the reference
# for who owns a handle: the resolver's paymentAddress must equal Kora's resolved ada address.
#
# A DB-vs-API check can't catch data that is wrong but consistently stored (e.g. the CIP-68 name
# bug); this one can. Mismatches are re-checked after RECHECK_SEC on both sides, so a handle that
# moved between the two reads doesn't fail the run.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

ORACLE_SAMPLE_N="${ORACLE_SAMPLE_N:-200}"
# NFT sub-handles (sub@root, CIP-68) are ~1% of handles, so sample them separately too.
ORACLE_SUBHANDLE_N="${ORACLE_SUBHANDLE_N:-50}"
RECHECK_SEC="${RECHECK_SEC:-60}"
export RECHECK_SEC

echo "[oracle] $ORACLE_SAMPLE_N random handles + $ORACLE_SUBHANDLE_N NFT sub-handles vs $ORACLE_API"
# Virtual sub-handles (label-0 prefix, reported by check-data-integrity) are left out.
sample=$(mktemp)
trap 'rm -f "$sample"' EXIT
sql "SELECT coalesce(json_agg(DISTINCT name), '[]') FROM (
       (SELECT name FROM ada_handle WHERE name <> '' AND name NOT LIKE '    %' ORDER BY random() LIMIT $ORACLE_SAMPLE_N)
       UNION ALL
       (SELECT name FROM ada_handle WHERE name LIKE '%@%' AND name NOT LIKE '    %' ORDER BY random() LIMIT $ORACLE_SUBHANDLE_N)) t;" > "$sample"

python3 -I - "$sample" <<'PY' || FAILED=1
import json, os, sys, time, urllib.error, urllib.parse, urllib.request

api, oracle = os.environ["API"], os.environ["ORACLE_API"]
headers = {"Accept": "application/json", "User-Agent": os.environ["USER_AGENT"]}
recheck = int(os.environ.get("RECHECK_SEC", "60"))

def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, None

def ours(name):
    status, body = get(f"{api}/api/v1/addresses/by-ada-handle/{urllib.parse.quote(name, safe='')}")
    return body.get("paymentAddress") if status == 200 else f"HTTP {status}"

def theirs(name):
    status, body = get(f"{oracle}/handles/{urllib.parse.quote(name, safe='')}")
    time.sleep(0.25)  # stay polite with the public API
    if status != 200:
        return f"HTTP {status}", None
    return (body.get("resolved_addresses") or {}).get("ada"), body.get("hex", "")

names = json.load(open(sys.argv[1]))
matched, mismatched, not_in_oracle, unavailable, cip68, subhandles = 0, [], [], 0, 0, 0
for name in names:
    expected, hexname = theirs(name)
    if expected == "HTTP 404":
        not_in_oracle.append(name)
        continue
    if expected and expected.startswith("HTTP "):
        unavailable += 1  # rate limit or outage on Kora's side; not a resolver result
        continue
    if hexname and hexname.startswith("000de140"):
        cip68 += 1
    if "@" in name:
        subhandles += 1
    if ours(name) == expected:
        matched += 1
    else:
        mismatched.append(name)

if mismatched:
    print(f"  INFO   {len(mismatched)} mismatches; re-checking in {recheck}s in case they moved meanwhile")
    time.sleep(recheck)
    still = []
    for name in mismatched:
        expected, _ = theirs(name)
        actual = ours(name)
        if actual == expected:
            matched += 1
        else:
            still.append((name, actual, expected))
    mismatched = still

checked = len(names) - len(not_in_oracle) - unavailable
if unavailable:
    print(f"  INFO   {unavailable} lookups failed on Kora's side (rate limit/outage) and were skipped")
if checked < len(names) // 2:
    print(f"  FAIL   only {checked}/{len(names)} handles could be compared; Kora unavailable?")
    sys.exit(1)
print(f"  INFO   sample: {len(names)} handles, {cip68} of them CIP-68, {subhandles} NFT sub-handles")
if not mismatched:
    print(f"  PASS   {matched}/{checked} handles resolve to the same address as Kora")
else:
    print(f"  FAIL   {len(mismatched)}/{checked} handles resolve to a different address than Kora:")
    for name, actual, expected in mismatched[:20]:
        print(f"           {name}: ours={actual} kora={expected}")
if not_in_oracle:
    print(f"  INFO   {len(not_in_oracle)} handles unknown to Kora (burned? the resolver keeps burned handles): "
          + ", ".join(not_in_oracle[:10]))
sys.exit(1 if mismatched else 0)
PY

exit $FAILED
