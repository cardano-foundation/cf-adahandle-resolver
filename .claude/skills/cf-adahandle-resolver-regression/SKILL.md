---
name: cf-adahandle-resolver-regression
description: QA regression suite for a synced cf-adahandle-resolver mainnet instance — checks what is deployed, that the indexer follows the tip with zero parse errors, database invariants (incl. the rollback-repair owner invariant), golden handles, a random sample against Kora Labs' Handles API as the source of truth, the REST/OpenAPI surface, and (opt-in) that an API restart keeps CIP-68 handles (regression for #54). Use after the full-sync skill reached tip, before promoting develop to main, or when the user asks to "run regression tests" / "check the synced instance".
---

# Regression suite for a synced mainnet instance

You are acting as a **QA Automation Engineer**. The
`cf-adahandle-resolver-mainnet-integration-test-full-sync` skill gets an instance to the tip
(hours); this skill judges it (minutes), and can be re-run at any time against the same instance.

It reuses the full-sync skill's configuration (`config.env`, `load-config.sh`): same SSH host,
remote dir, branch and tunnel ports. Nothing to configure twice.

## Preconditions

- The instance was deployed by the full-sync skill and is **at the tip**. Mid-sync results are
  meaningless (handles minted after the cursor don't exist yet).
- The SSH tunnel is open (`../cf-adahandle-resolver-mainnet-integration-test-full-sync/scripts/open-tunnels.sh`)
  and `http://localhost:$LOCAL_API_PORT/apidocs` answers.
- Internet access from your machine to `api.handle.me` (oracle) and `api.koios.rest` (tip).

## Run

```bash
scripts/run-all.sh                  # read-only checks
scripts/run-all.sh --with-restart   # + restart regression: RESTARTS the API container
```

Each check can also run alone (`scripts/check-oracle.sh`, …). `run-all.sh` writes a Markdown
report to `reports/` (gitignored); attach it to the promotion PR.

**Ask the user before `--with-restart`** unless they asked for it: it restarts the API of the
instance under test (the indexer resumes from its cursor; the API is down for ~15 s).

## Checks

| Check | Proves | Fails when |
|---|---|---|
| `check-deployment` | What is under test: deployed commit vs branch head, image build time, Java, key libraries in the jar | the API container isn't running |
| `check-api` | OpenAPI spec, Swagger UI, sync status, 404 for unknown handles, empty list for unknown addresses, all resolver paths in the spec | any endpoint deviates |
| `check-sync-health` | At the tip (Koios), cursor advancing, era (default Conway = 7), zero block-parse errors; summarises ERROR lines and rollback activity | lag > `MAX_LAG_SLOTS` (600), cursor older than `MAX_CURSOR_AGE_SEC` (600), wrong era, any parse error |
| `check-data-integrity` | No asset fingerprints as names (CIP-68 decoding); every handle has history and vice versa; **every handle's current owner equals its latest history entry** (guards the rollback repair) | any invariant broken |
| `golden` | The famous handles in the full-sync skill's `golden-handles.txt` resolve and round-trip | any golden handle fails |
| `check-oracle` | `ORACLE_SAMPLE_N` (200) truly random handles resolve to the same address as Kora's API; mismatches are re-checked after `RECHECK_SEC` (60 s) on both sides | any persistent mismatch, or under half the sample comparable |
| `check-restart-rollback` | `CIP68_N` (20) real CIP-68 handles still resolve to Kora's address after an API restart; handle count doesn't drop | any handle changes or the count drops |

### Why an oracle

The full-sync skill's spot-check compares the API with the resolver's own database, so it can't see
data that is wrong but consistently stored. That is exactly how CIP-68 handles were lost on every
restart before #54. Kora Labs' Handles API is independent, so `check-oracle` and
`check-restart-rollback` compare against it. Notes:

- `api.handle.me` answers 403 to a default client User-Agent; the scripts send their own.
- Kora's `/stats` total isn't comparable with our row count (different definition of a handle); compare per handle, never totals.
- Handles unknown to Kora are listed, not failed: the resolver keeps burned handles (it never removes a handle that was burned).

## Reading results

- `PASS` / `FAIL` per assertion; a check fails if any of its assertions failed.
- `KNOWN` marks a known limitation that is reported but doesn't fail the run. Keep this list short
  and backed by an issue; remove an entry once it's fixed:
  - **Virtual sub-handles (CIP-67 label 0)**: stored with their 4-byte label prefix (shown as four
    leading spaces) and the script address that holds the token, so they don't resolve by name. Their
    address lives in the token's datum, which the resolver doesn't read.
  - **`Error stopping blockRangeSync`** (yaci-helper 0.4.7): an NPE in `BlockRangeSync.stop()` during the
    catch-up after a restart; harmless.

Report a FAIL with the check's output (it names the handles/addresses involved), the deployed
commit from `check-deployment`, and the report file.

## Files

```
.claude/skills/cf-adahandle-resolver-regression/
├── SKILL.md
├── reports/                      # run-all.sh output (gitignored)
└── scripts/
    ├── lib.sh                    # config (from the full-sync skill), ssh/sql helpers, PASS/FAIL output
    ├── run-all.sh                # runs the suite, writes the report
    ├── check-deployment.sh
    ├── check-api.sh
    ├── check-sync-health.sh
    ├── check-data-integrity.sh
    ├── check-oracle.sh
    └── check-restart-rollback.sh # restarts the API; needs --yes
```
