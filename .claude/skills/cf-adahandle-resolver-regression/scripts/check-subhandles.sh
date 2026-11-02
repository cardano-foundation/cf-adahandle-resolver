#!/usr/bin/env bash
# Every sub-handle (name@root) against Kora Labs' Handles API, for each root that has sub-handles in
# the resolver: GET /handles/{root}/subhandles lists NFT and virtual sub-handles with their resolved
# address. Reports, per type (nft_subhandle / virtual_subhandle):
#   - addresses that differ from Kora (re-checked after RECHECK_SEC on both sides): FAIL
#   - sub-handles Kora lists but the resolver doesn't know: FAIL
#   - sub-handles the resolver knows but Kora doesn't list: INFO (the resolver keeps burned handles)

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

RECHECK_SEC="${RECHECK_SEC:-60}"
export RECHECK_SEC

echo "[subhandles] all sub-handles vs $ORACLE_API"
ours=$(mktemp)
trap 'rm -f "$ours"' EXIT
sql "SELECT coalesce(json_object_agg(name, payment_address), '{}') FROM ada_handle WHERE name LIKE '%@%';" > "$ours"

python3 -I - "$ours" <<'PY' || FAILED=1
import collections, json, os, sys, time, urllib.error, urllib.parse, urllib.request

api, oracle = os.environ["API"], os.environ["ORACLE_API"]
headers = {"Accept": "application/json", "User-Agent": os.environ["USER_AGENT"]}
recheck = int(os.environ["RECHECK_SEC"])

def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, None

def kora_address(handle):
    return (handle.get("resolved_addresses") or {}).get("ada")

ours = json.load(open(sys.argv[1]))
roots = sorted({name.split("@", 1)[1] for name in ours})
print(f"  INFO   {len(ours)} sub-handles under {len(roots)} roots in the resolver")

kora, kora_type, unavailable = {}, {}, []
for root in roots:
    status, listed = get(f"{oracle}/handles/{urllib.parse.quote(root, safe='')}/subhandles")
    time.sleep(0.25)
    if status != 200 or not isinstance(listed, list):
        unavailable.append(f"{root} (HTTP {status})")
        continue
    for handle in listed:
        kora[handle["name"]] = kora_address(handle)
        kora_type[handle["name"]] = handle.get("handle_type", "?")
if unavailable:
    print(f"  INFO   Kora listing unavailable for {len(unavailable)} roots: {', '.join(unavailable[:5])}")

covered = {n for n in ours if n.split("@", 1)[1] not in {u.split(' ')[0] for u in unavailable}}
stats = collections.defaultdict(collections.Counter)
mismatched = []
for name in covered:
    if name not in kora:
        stats["(not in Kora)"]["extra"] += 1
        continue
    t = kora_type[name]
    if ours[name] == kora[name]:
        stats[t]["match"] += 1
    else:
        mismatched.append(name)
missing = sorted(n for n in kora if n not in ours)
for n in missing:
    stats[kora_type[n]]["missing"] += 1

if mismatched:
    print(f"  INFO   {len(mismatched)} address differences; re-checking in {recheck}s in case they changed meanwhile")
    time.sleep(recheck)
    still = []
    for name in mismatched:
        s1, k = get(f"{oracle}/handles/{urllib.parse.quote(name, safe='')}")
        s2, o = get(f"{api}/api/v1/addresses/by-ada-handle/{urllib.parse.quote(name, safe='')}")
        theirs = kora_address(k) if s1 == 200 else f"HTTP {s1}"
        mine = o.get("paymentAddress") if s2 == 200 else f"HTTP {s2}"
        if theirs == mine:
            stats[kora_type[name]]["match"] += 1
        else:
            stats[kora_type[name]]["DIFFERENT"] += 1
            still.append((name, mine, theirs))
        time.sleep(0.25)
    mismatched = still

for t in sorted(stats):
    print(f"  INFO   {t}: " + ", ".join(f"{k} {v}" for k, v in sorted(stats[t].items())))
failed = False
if mismatched:
    failed = True
    print(f"  FAIL   {len(mismatched)} sub-handles resolve to a different address than Kora:")
    for name, mine, theirs in mismatched[:15]:
        print(f"           {name} ({kora_type.get(name)}): ours={mine} kora={theirs}")
if missing:
    failed = True
    print(f"  FAIL   {len(missing)} sub-handles listed by Kora are missing in the resolver: "
          + ", ".join(f"{n} ({kora_type[n]})" for n in missing[:15]))
if not failed:
    matched = sum(c["match"] for c in stats.values())
    print(f"  PASS   {matched} sub-handles resolve to the same address as Kora; none missing")
extra = stats["(not in Kora)"]["extra"]
if extra:
    print(f"  INFO   {extra} sub-handles not listed by Kora (burned? the resolver keeps burned handles)")
sys.exit(1 if failed else 0)
PY

exit $FAILED
