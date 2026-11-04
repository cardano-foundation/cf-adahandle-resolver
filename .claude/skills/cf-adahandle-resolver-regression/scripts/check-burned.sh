#!/usr/bin/env bash
# Every handle the resolver treats as burned must be gone on Kora Labs' Handles API too.
#
# The other Kora comparisons start from handles the resolver has, so they can't see a handle it
# removed by mistake. This one starts from the resolver's burns: each handle whose latest history
# row is a burn (so it has no ada_handle row) must answer 404 on Kora. A legacy handle migrated to
# CIP-68 (its legacy token burned and a CIP-68 token of the same name minted in one transaction)
# is the case it was written for.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

echo "[burned] handles the resolver removed as burned vs $ORACLE_API"
burned=$(mktemp)
trap 'rm -f "$burned"' EXIT
sql "SELECT coalesce(json_agg(h.name), '[]') FROM ada_handle_history_item h
       JOIN (SELECT name, max(slot) AS slot FROM ada_handle_history_item GROUP BY name) l
         ON l.name = h.name AND l.slot = h.slot
      WHERE h.burned;" > "$burned"

python3 -I - "$burned" <<'PY' || FAILED=1
import json, os, sys, time, urllib.error, urllib.parse, urllib.request

oracle = os.environ["ORACLE_API"]
headers = {"Accept": "application/json", "User-Agent": os.environ["USER_AGENT"]}

def status(name):
    url = f"{oracle}/handles/{urllib.parse.quote(name, safe='')}"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    finally:
        time.sleep(0.25)  # stay polite with the public API

names = json.load(open(sys.argv[1]))
gone, alive, unavailable = 0, [], 0
for name in names:
    code = status(name)
    if code == 404:
        gone += 1
    elif code == 200:
        alive.append(name)
    else:
        unavailable += 1  # rate limit, outage, or 202 while Kora catches up

print(f"  INFO   {len(names)} handles removed as burned")
if unavailable:
    print(f"  INFO   {unavailable} lookups gave no answer on Kora's side and were skipped")
if len(names) and gone + len(alive) < len(names) // 2:
    print(f"  FAIL   only {gone + len(alive)}/{len(names)} burned handles could be checked; Kora unavailable?")
    sys.exit(1)
if alive:
    print(f"  FAIL   {len(alive)} handles the resolver removed as burned still resolve on Kora: " + ", ".join(alive[:10]))
    sys.exit(1)
print(f"  PASS   all {gone} checked burned handles are gone on Kora too")
PY

exit $FAILED
