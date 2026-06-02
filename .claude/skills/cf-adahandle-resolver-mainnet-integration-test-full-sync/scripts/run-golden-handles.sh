#!/usr/bin/env bash
# Golden-handles regression check against the tunneled API.
#
# Reads commonly-known mainnet handles from ../golden-handles.txt and, for each,
# asserts (against http://localhost:$LOCAL_API_PORT):
#   forward: GET /api/v1/addresses/by-ada-handle/{handle}
#            -> HTTP 200, paymentAddress well-formed (addr1.../addr_test1...),
#               stakeAddress either absent (enterprise address) or well-formed (stake1...)
#   reverse: GET /api/v1/ada-handles/by-payment-address/{paymentAddress}
#            -> the handle is present in the returned list
#
# Reverse uses the PAYMENT address (always present) rather than stake, so it also
# works for enterprise-address handles that have no stake address.
#
# Drift-proof by design: no addresses are hardcoded — see golden-handles.txt for
# the rationale. Run at tip (a handle minted after a mid-sync cursor won't exist
# yet and will be reported missing).
#
# Exits 0 only if every golden handle passes both directions.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/load-config.sh"

LIST="$SKILL_DIR/golden-handles.txt"
if [[ ! -s "$LIST" ]]; then
  echo "ERROR: golden list missing or empty: $LIST" >&2
  exit 1
fi

echo "[golden] checking handles from $(basename "$LIST") against http://localhost:${LOCAL_API_PORT} ..."

# Pass the list path as argv[1] and read it inside Python. (Do NOT pipe data to
# `python3 -`: the heredoc is consumed as the program from stdin, so a piped
# stdin would be empty.)
API="http://localhost:${LOCAL_API_PORT}" python3 - "$LIST" <<'PY'
import sys, os, json, urllib.parse, urllib.request, re

api = os.environ["API"]
ADDR = re.compile(r"^addr(_test)?1[0-9a-z]+$")
STAKE = re.compile(r"^stake(_test)?1[0-9a-z]+$")

def get(path):
    try:
        with urllib.request.urlopen(urllib.request.Request(api + path), timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, None

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

print(f"[golden] {passed}/{total} golden handles passed forward+reverse round-trip")
for n, m in fails:
    print(f"  FAIL {n}: {'; '.join(m)}")

sys.exit(0 if (total > 0 and passed == total) else 1)
PY
