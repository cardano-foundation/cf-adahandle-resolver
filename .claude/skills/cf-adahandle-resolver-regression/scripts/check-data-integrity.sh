#!/usr/bin/env bash
# Database invariants of the indexed data. The owner invariant guards the rollback repair: after
# any rollback, ada_handle must still equal the latest history entry of every handle.

set -euo pipefail
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

echo "[data-integrity]"
IFS='|' read -r handles history size < <(sql "SELECT (SELECT count(*) FROM ada_handle), (SELECT count(*) FROM ada_handle_history_item), pg_size_pretty(pg_database_size(current_database()));")
info "$handles handles, $history history rows, database $size"
(( handles > 0 )) && pass "ada_handle is populated" || fail "ada_handle is empty"

fingerprints=$(sql "SELECT count(*) FROM ada_handle WHERE name LIKE 'asset1%';")
(( fingerprints == 0 )) && pass "no asset fingerprints stored as handle names (CIP-68 decoding)" \
  || fail "$fingerprints handles stored under an asset fingerprint name (CIP-68 names not decoded)"

# CIP-67 label 0 (bytes 00000000; yaci turns NULs into spaces) is used for virtual sub-handles.
# Their address is in the token's datum, not the holder of the token. Not supported yet.
virtual=$(sql "SELECT count(*) FROM ada_handle WHERE name LIKE '    %';")
(( virtual == 0 )) && pass "no undecoded label-0 names" \
  || known "$virtual virtual sub-handles (CIP-67 label 0) stored with their label prefix and the script address holding the token; they don't resolve by name"

# CIP-67 label 1 (bytes 00001070, shown as two spaces, 0x10 and 'p') are companion tokens held by
# Handle scripts; the real handle is a separate label-222 token. Should be skipped like label 100.
label1=$(sql "SELECT count(*) FROM ada_handle WHERE encode(convert_to(name, 'UTF8'), 'hex') LIKE '20201070%';")
(( label1 == 0 )) && pass "no CIP-67 label-1 companion tokens stored as handles" \
  || known "$label1 CIP-67 label-1 companion tokens stored as handles (held by Handle scripts; the real handles are stored separately)"

no_history=$(sql "SELECT count(*) FROM ada_handle h WHERE NOT EXISTS (SELECT 1 FROM ada_handle_history_item i WHERE i.name = h.name);")
(( no_history == 0 )) && pass "every handle has history" || fail "$no_history handles without any history row"

orphans=$(sql "SELECT count(*) FROM (SELECT DISTINCT name FROM ada_handle_history_item) n WHERE NOT EXISTS (SELECT 1 FROM ada_handle h WHERE h.name = n.name);")
(( orphans == 0 )) && pass "every handle in history exists in ada_handle" || fail "$orphans handle names in history but missing from ada_handle"

drift=$(sql "SELECT count(*) FROM ada_handle h CROSS JOIN LATERAL (
               SELECT i.stake_address, i.payment_address FROM ada_handle_history_item i
               WHERE i.name = h.name ORDER BY i.slot DESC LIMIT 1) latest
             WHERE latest.payment_address IS DISTINCT FROM h.payment_address
                OR latest.stake_address IS DISTINCT FROM h.stake_address;")
(( drift == 0 )) && pass "every handle's current owner equals its latest history entry" \
  || fail "$drift handles whose current owner differs from their latest history entry (rollback repair?)"

exit $FAILED
