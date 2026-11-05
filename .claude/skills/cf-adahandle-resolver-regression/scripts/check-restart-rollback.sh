#!/usr/bin/env bash
# Regression for #54: restarting the API must not lose or corrupt CIP-68 handles.
#
# yaci-store publishes a RollbackEvent on every start ("clean data after restart"). Before #54,
# handling it rebuilt ada_handle from history, which dropped every CIP-68 handle. This check picks
# real CIP-68 handles (Kora hex prefix 000de140), checks they resolve to Kora's address, restarts
# the API, waits for the restart rollback to be handled, and checks them again.
#
# RESTARTS THE API CONTAINER. Run only with --yes (run-all.sh passes it for --with-restart).

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

[[ "${1:-}" == "--yes" ]] || { echo "refusing to restart $API_CONTAINER without --yes" >&2; exit 2; }
CIP68_N="${CIP68_N:-20}"

echo "[restart-rollback] $CIP68_N CIP-68 handles across an API restart"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
sql "SELECT coalesce(json_agg(name), '[]') FROM (SELECT name FROM ada_handle
       WHERE name <> '' AND name NOT LIKE '    %' ORDER BY random() LIMIT $(( CIP68_N * 10 ))) t;" > "$work/candidates.json"

resolve() {  # resolve <names.json> <out.json> [pick N CIP-68 handles via Kora]
  python3 -I - "$@" <<'PY'
import json, os, sys, time, urllib.error, urllib.parse, urllib.request
headers = {"Accept": "application/json", "User-Agent": os.environ["USER_AGENT"]}
def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=20) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, None
def ours(name):
    status, body = get(f"{os.environ['API']}/api/v1/addresses/by-ada-handle/{urllib.parse.quote(name, safe='')}")
    return body.get("paymentAddress") if status == 200 else f"HTTP {status}"
names, out = json.load(open(sys.argv[1])), sys.argv[2]
if len(sys.argv) > 3:  # pick CIP-68 handles that currently resolve to Kora's address
    picked = {}
    for name in names:
        status, body = get(f"{os.environ['ORACLE_API']}/handles/{urllib.parse.quote(name, safe='')}")
        time.sleep(0.25)
        if status == 200 and body.get("hex", "").startswith("000de140"):
            expected = (body.get("resolved_addresses") or {}).get("ada")
            if ours(name) == expected:
                picked[name] = expected
        if len(picked) >= int(sys.argv[3]):
            break
    json.dump(picked, open(out, "w"))
    print(len(picked))
else:
    json.dump({n: ours(n) for n in names}, open(out, "w"))
PY
}

picked=$(resolve "$work/candidates.json" "$work/before.json" "$CIP68_N")
if (( picked == 0 )); then
  fail "found no CIP-68 handle resolving to Kora's address before the restart"
  exit 1
fi
info "$picked CIP-68 handles resolve to Kora's address before the restart"
count_before=$(sql "SELECT count(*) FROM ada_handle;")

ssh_exec "cd $REMOTE_PROJECT_DIR && docker compose --env-file $ENV_FILE restart api" >/dev/null 2>&1
started=$(ssh_exec "docker inspect -f '{{.State.StartedAt}}' $API_CONTAINER")
info "API restarted at $started; waiting for the restart rollback"
for _ in $(seq 1 150); do
  if ssh_exec "docker logs --since '$started' $API_CONTAINER 2>&1 | grep -q 'Rollback to slot .*\(deleted\|across\)'"; then
    break
  fi
  sleep 2
done
handled=$(ssh_exec "docker logs --since '$started' $API_CONTAINER 2>&1 | grep -m1 -E 'Rollback to slot .*(deleted|across)' | sed -E 's/^.*Rollback to slot/Rollback to slot/'" || true)
[[ -n "$handled" ]] && pass "restart rollback handled: $handled" || fail "no restart rollback logged within 5 minutes"
for _ in $(seq 1 60); do curl -sf -o /dev/null "$API/apidocs" && break; sleep 2; done

python3 -I -c 'import json,sys; print(json.dumps(list(json.load(open(sys.argv[1])))))' "$work/before.json" > "$work/names.json"
resolve "$work/names.json" "$work/after.json"
python3 -I - "$work/before.json" "$work/after.json" <<'PY' || FAILED=1
import json, sys
before, after = json.load(open(sys.argv[1])), json.load(open(sys.argv[2]))
lost = {n: (before[n], after.get(n)) for n in before if after.get(n) != before[n]}
if not lost:
    print(f"  PASS   {len(before)}/{len(before)} CIP-68 handles still resolve to Kora's address after the restart")
else:
    print(f"  FAIL   {len(lost)}/{len(before)} CIP-68 handles changed across the restart:")
    for n, (b, a) in list(lost.items())[:10]:
        print(f"           {n}: before={b} after={a}")
    sys.exit(1)
PY

count_after=$(sql "SELECT count(*) FROM ada_handle;")
# New mints may land meanwhile; a restart must never remove handles.
(( count_after >= count_before )) && pass "handle count $count_before -> $count_after" \
  || fail "handle count dropped across the restart: $count_before -> $count_after"

exit $FAILED
