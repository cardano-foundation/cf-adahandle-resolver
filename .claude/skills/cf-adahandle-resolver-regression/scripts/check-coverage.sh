#!/usr/bin/env bash
# Starts from the chain, not from the resolver's database: lists the handle tokens of every ADA
# Handle minting policy (Koios) and checks that the resolver knows each one. The other checks
# sample handles the resolver already has, so they can't see handles it never indexed (e.g. the
# DeMi policy's, until it was added).
#
# Per policy: all handles when it has at most COVERAGE_FULL_MAX assets, otherwise COVERAGE_SAMPLE_N
# from random pages. Handle tokens are legacy names (no CIP-67 label), label 222 and label 0
# (virtual sub-handles); burned ones (supply 0) are skipped. A handle the resolver doesn't know is
# re-checked after RECHECK_SEC (it may be newer than the resolver's cursor), and only fails if
# Kora's API knows it.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

# Kora Labs' handle policies, the same on mainnet, preprod and preview
# (koralabs/kora-labs-common, src/handles/policies.ts): the original one and DeMi's.
HANDLE_POLICIES="${HANDLE_POLICIES:-f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a 6c32db33a422e0bc2cb535bb850b5a6e9a9572222056d6ddc9cbc26e}"
KOIOS_API="${KOIOS_API:-https://api.koios.rest/api/v1}"
COVERAGE_FULL_MAX="${COVERAGE_FULL_MAX:-2000}"
COVERAGE_SAMPLE_N="${COVERAGE_SAMPLE_N:-200}"
RECHECK_SEC="${RECHECK_SEC:-60}"
export HANDLE_POLICIES KOIOS_API COVERAGE_FULL_MAX COVERAGE_SAMPLE_N RECHECK_SEC

echo "[coverage] handle tokens of every ADA Handle policy (Koios) known to the resolver"

python3 -I - <<'PY' || FAILED=1
import json, os, random, time, urllib.error, urllib.parse, urllib.request

api, oracle, koios = os.environ["API"], os.environ["ORACLE_API"], os.environ["KOIOS_API"]
headers = {"Accept": "application/json", "User-Agent": os.environ["USER_AGENT"]}
full_max, sample_n = int(os.environ["COVERAGE_FULL_MAX"]), int(os.environ["COVERAGE_SAMPLE_N"])
recheck = int(os.environ["RECHECK_SEC"])
PAGE = 50
# Sampled pages are small: names are sorted, so a page is a run of similar ones (e.g. 50 x@hal).
SAMPLE_PAGE = 5

def request(url, extra=None):
    req = urllib.request.Request(url, headers={**headers, **(extra or {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.headers, json.load(r)

def status(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code

def assets(policy, offset, limit):
    # Paging needs a stable order: without one, pages overlap and skip rows.
    query = urllib.parse.urlencode({"_asset_policy": policy, "select": "asset_name,total_supply",
                                    "order": "asset_name.asc", "offset": offset, "limit": limit})
    response_headers, rows = request(f"{koios}/policy_asset_list?{query}", {"Prefer": "count=exact"})
    total = int(response_headers.get("Content-Range", "*/0").rsplit("/", 1)[1])
    time.sleep(0.2)
    return total, rows

def crc8(data):
    crc = 0
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = ((crc << 1) ^ 0x07) & 0xFF if crc & 0x80 else (crc << 1) & 0xFF
    return crc

def cip67_label(name):
    if len(name) < 4 or name[0] & 0xF0 or name[3] & 0x0F:
        return None
    label = ((name[0] << 12) | (name[1] << 4) | (name[2] >> 4)) & 0xFFFF
    checksum = ((name[2] & 0x0F) << 4) | (name[3] >> 4)
    return label if crc8(bytes([label >> 8, label & 0xFF])) == checksum else None

def handle_name(row):
    """The handle a token carries, or None if it isn't a live handle token."""
    if int(row.get("total_supply") or 0) <= 0:
        return None
    name = bytes.fromhex(row["asset_name"])
    label = cip67_label(name)
    if label is None:
        return name.decode("utf-8", "replace")
    if label in (222, 0):
        return name[4:].decode("utf-8", "replace")
    return None

def known(name):
    return status(f"{api}/api/v1/addresses/by-ada-handle/{urllib.parse.quote(name, safe='')}") == 200

failed = False
for policy in os.environ["HANDLE_POLICIES"].split():
    total, rows = assets(policy, 0, PAGE)
    if total <= full_max:
        for offset in range(PAGE, total, PAGE):
            rows += assets(policy, offset, PAGE)[1]
        scope = f"all {total} assets"
    else:
        rows = []
        for offset in random.sample(range(0, total, SAMPLE_PAGE), sample_n // SAMPLE_PAGE):
            rows += assets(policy, offset, SAMPLE_PAGE)[1]
        scope = f"{len(rows)} of {total} assets, from random pages"
    names = sorted({n for n in map(handle_name, rows) if n})
    missing = [n for n in names if not known(n)]
    if missing:
        print(f"  INFO   {policy[:8]}…: {len(missing)} unknown to the resolver; re-checking in {recheck}s")
        time.sleep(recheck)
        missing = [n for n in missing if not known(n)]
    not_in_kora = [n for n in missing if status(f"{oracle}/handles/{urllib.parse.quote(n, safe='')}") == 404]
    missing = [n for n in missing if n not in not_in_kora]
    print(f"  INFO   {policy[:8]}…: {scope}; {len(names)} live handle tokens")
    if not_in_kora:
        print(f"  INFO   {policy[:8]}…: {len(not_in_kora)} unknown to Kora too (not handles): {', '.join(not_in_kora[:5])}")
    if missing:
        failed = True
        print(f"  FAIL   {policy[:8]}…: {len(missing)} handles Kora resolves are unknown to the resolver: "
              + ", ".join(missing[:15]))
    else:
        print(f"  PASS   {policy[:8]}…: the resolver knows all {len(names) - len(not_in_kora)} handles")
raise SystemExit(1 if failed else 0)
PY

exit $FAILED
