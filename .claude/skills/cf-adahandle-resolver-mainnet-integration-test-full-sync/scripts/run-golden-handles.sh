#!/usr/bin/env bash
# Golden-handles regression check against the tunneled API.
#
# Reads well-known mainnet handles, at least one of every kind, from ../golden-handles.txt and,
# for each, asserts (against http://localhost:$LOCAL_API_PORT):
#   forward: GET /api/v1/addresses/by-ada-handle/{handle}
#            -> HTTP 200, paymentAddress well-formed (addr1.../addr_test1...),
#               stakeAddress either absent (enterprise address) or well-formed (stake1...)
#   Kora:    paymentAddress equals Kora Labs' resolved ADA address for the handle
#            (GET $ORACLE_API/handles/{handle}); a difference is re-checked on both sides after
#            RECHECK_SEC, in case the handle moved between the two reads. GOLDEN_KORA=0 skips it.
#   reverse: GET /api/v1/ada-handles/by-payment-address/{paymentAddress}
#            -> the handle is present in the returned list
#
# Reverse uses the PAYMENT address (always present) rather than stake, so it also
# works for enterprise-address handles that have no stake address.
#
# No addresses are hardcoded (handles change owner): the expected one comes from Kora at run
# time. Run at tip (a handle minted after a mid-sync cursor won't exist yet and will be
# reported missing).
#
# Exits 0 only if every golden handle passes all checks.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

LIST="$SKILL_DIR/golden-handles.txt"
if [[ ! -s "$LIST" ]]; then
  echo "ERROR: golden list missing or empty: $LIST" >&2
  exit 1
fi

ORACLE_API="${ORACLE_API:-https://api.handle.me}"
GOLDEN_KORA="${GOLDEN_KORA:-1}"
RECHECK_SEC="${RECHECK_SEC:-60}"
echo "[golden] checking handles from $(basename "$LIST") against http://localhost:${LOCAL_API_PORT} and $ORACLE_API ..."

# Pass the list path as argv[1] and read it inside Python. (Do NOT pipe data to
# `python3 -`: the heredoc is consumed as the program from stdin, so a piped
# stdin would be empty.)
API="http://localhost:${LOCAL_API_PORT}" ORACLE_API="$ORACLE_API" GOLDEN_KORA="$GOLDEN_KORA" RECHECK_SEC="$RECHECK_SEC" \
  python3 - "$LIST" <<'PY'
import sys, os, json, time, urllib.parse, urllib.request, re

api, oracle = os.environ["API"], os.environ["ORACLE_API"]
use_kora, recheck = os.environ["GOLDEN_KORA"] != "0", int(os.environ["RECHECK_SEC"])
# api.handle.me rejects requests with a default client User-Agent (HTTP 403).
KORA_HEADERS = {"Accept": "application/json", "User-Agent": "cf-adahandle-resolver-regression/1.0"}
ADDR = re.compile(r"^addr(_test)?1[0-9a-z]+$")
STAKE = re.compile(r"^stake(_test)?1[0-9a-z]+$")

def get(path):
    try:
        with urllib.request.urlopen(urllib.request.Request(api + path), timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, None

def ours(name):
    st, body = get(f"/api/v1/addresses/by-ada-handle/{urllib.parse.quote(name, safe='')}")
    return (body or {}).get("paymentAddress") if st == 200 else f"HTTP {st}"

def kora(name):
    req = urllib.request.Request(f"{oracle}/handles/{urllib.parse.quote(name, safe='')}", headers=KORA_HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            body = json.load(r)
    except urllib.error.HTTPError as e:
        return f"HTTP {e.code}"
    finally:
        time.sleep(0.25)  # stay polite with the public API
    return (body.get("resolved_addresses") or {}).get("ada")

with open(sys.argv[1]) as f:
    names = [l.strip() for l in f]

total = passed = 0
fails = []
for name in names:
    if not name or name.startswith("#"):
        continue
    total += 1
    msgs = []

    st, body = get(f"/api/v1/addresses/by-ada-handle/{urllib.parse.quote(name, safe='')}")
    payment = None
    if st != 200 or not body:
        msgs.append(f"forward HTTP {st}")
    else:
        payment = body.get("paymentAddress")
        stake = body.get("stakeAddress")
        if not payment or not ADDR.match(payment):
            msgs.append(f"bad paymentAddress: {payment!r}")
        if stake and not STAKE.match(stake):
            msgs.append(f"bad stakeAddress: {stake!r}")

    if use_kora and payment and ADDR.match(payment):
        expected = kora(name)
        if expected != payment:
            time.sleep(recheck)
            payment, expected = ours(name), kora(name)
            if expected != payment:
                msgs.append(f"differs from Kora: ours={payment} kora={expected}")

    if payment and ADDR.match(payment):
        st, arr = get(f"/api/v1/ada-handles/by-payment-address/{urllib.parse.quote(payment, safe='')}")
        if st != 200 or arr is None:
            msgs.append(f"reverse HTTP {st}")
        elif name not in arr:
            msgs.append(f"reverse missing: '{name}' not in {arr}")

    if msgs:
        fails.append((name, msgs))
    else:
        passed += 1

checks = "forward, Kora and reverse" if use_kora else "forward and reverse (Kora skipped)"
print(f"[golden] {passed}/{total} golden handles passed {checks}")
for n, m in fails:
    print(f"  FAIL {n}: {'; '.join(m)}")

sys.exit(0 if (total > 0 and passed == total) else 1)
PY
