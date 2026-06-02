---
name: cf-adahandle-resolver-mainnet-integration-test-full-sync
description: QA Automation Engineer — drives a full mainnet integration test of cf-adahandle-resolver on a remote host via SSH. Provisions a fresh Postgres + API stack with docker compose, syncing the chain from the first ADA Handle mint (slot 47931310) to tip, monitors window-relative progress from the DB cursor, then asserts the run is clean (zero block-parse errors through the Conway era / Van Rossem boundary) and that the REST API resolves handles consistently. Use when the user wants to validate a branch end-to-end against mainnet on a remote box (typed `/cf-adahandle-resolver-mainnet-integration-test-full-sync` or asked for a "mainnet full-sync regression run" / "hard-fork readiness sync test").
---

# Mainnet Full-Sync Integration Test (Remote, via SSH)

You are acting as a **QA Automation Engineer**. The user has a remote Linux box reachable over SSH that will:

1. Pull a fresh checkout of `cf-adahandle-resolver` on a target branch.
2. Spin up a clean Postgres + API stack via the project's `docker-compose.yml`, syncing mainnet from the first ADA Handle minting block (**slot 47931310**) up to tip.
3. As the indexer advances, it parses every block on the way to tip — **including the Conway era and the Van Rossem (protocol v11) boundary**. Parsing the whole window without error *is* the core regression: this is what proves the project's yaci-store/yaci-core versions are hard-fork ready.

Your job is to drive this remotely. You forward the remote API (port 9095) and Postgres (port 5432) to localhost via SSH so the regression spot-checks run on your local machine against the live remote stack.

## What "pass" means here

Unlike a fixture-based API test, the regression signal is the **full sync itself**:

1. **Sync reaches tip** — the DB cursor catches up to the live mainnet tip (window progress ≈ 100%), with the cursor era at **Conway** (Van Rossem is intra-Conway).
2. **Zero genuine block-parse errors** across the whole run — no `BlockParseRuntimeException` / "Block parsing error" whose root cause is a decode failure. (Errors whose root cause is "Database is already closed" / "context closed" are teardown/shutdown noise, not parse failures — see below.)
3. **REST spot-checks pass** — two complementary checks:
   - **Golden handles** (`golden-handles.txt`): a fixed set of commonly-known mainnet handles (`cardano`, `hosky`, `minswap`, `bigpey`, `charles`, …) must all resolve and round-trip. Deterministic and reviewable.
   - **Random DB sample**: for a sample of handles drawn from the synced DB, the API's forward lookup matches the stored row and the reverse lookup round-trips.

## Critical rules

- **Never blow away an existing instance silently.** If the remote already has a stack running, the Postgres volume populated, or the target ports occupied, **STOP** and report. Ask whether to clean up or abort. Don't `docker compose down -v` on your own.
- **Fresh sync means empty data.** A clean run requires the `adahandle-db` Postgres volume to be empty. If it already has data, surface it and let the user decide (wipe vs. resume).
- **Don't run the REST spot-checks too early.** They need the `ada_handle` table populated and the sync at (or near) tip. Running them mid-sync produces meaningless results.
- **Full sync is long.** From slot 47931310 to tip is multiple hours on a typical box. Set expectations before kicking off, and prefer long fallback waits (1200–1800s) when polling progress.
- **The build under test is THIS branch's source.** `start-remote.sh` builds the API image from the checked-out Dockerfile — not a published `:latest`.

## Required configuration

Load config from (in priority order):

1. CLI args passed to the skill: `--ssh-host`, `--ssh-user`, `--remote-dir`, `--branch`, `--env-file`.
2. `./.claude/skills/cf-adahandle-resolver-mainnet-integration-test-full-sync/config.env` (gitignored — not committed).
3. Defaults.

| Variable | Default | Required? |
|---|---|---|
| `REMOTE_SSH_HOST` | none | **yes** (host or ssh_config alias) |
| `REMOTE_SSH_USER` | none | no — leave empty when `REMOTE_SSH_HOST` is an ssh_config alias whose `User` directive already sets the login |
| `REMOTE_PROJECT_DIR` | `$HOME/git/cf-adahandle-resolver` | no |
| `BRANCH` | `main` | no |
| `GIT_REPO_URL` | `https://github.com/cardano-foundation/cf-adahandle-resolver.git` | no |
| `ENV_FILE` | `.env` (mainnet) | no |
| `LOCAL_API_PORT` | `9095` | no |
| `LOCAL_DB_PORT` | `5432` | no |
| `SAMPLE_N` | `10` | no — number of handles sampled for REST spot-checks |

If `REMOTE_SSH_HOST` is missing, refuse to start and tell the user to either supply it as an arg or fill in `config.env`. **Never invent or default a hostname.**

A template lives at `config.env.example`. Tell the user to `cp config.env.example config.env` and fill it in; the real `config.env` is gitignored.

## Procedure

You orchestrate via the helper scripts under `./scripts/`. They are intentionally small and atomic so each step is auditable. Source `scripts/load-config.sh` first to populate environment variables, then call the steps in order. All scripts are idempotent and safe to re-run.

### Step 0 — Confirm intent with the user

Before any remote action, echo back the resolved config (host, user, remote dir, branch, env file) and ask the user to confirm. A fresh sync is destructive to any existing data on the remote.

### Step 1 — Preflight check (read-only on remote)

Run `scripts/preflight-remote.sh`. It SSHs in and checks: a running compose stack in `$REMOTE_PROJECT_DIR`; whether ports 9095/5432 are already bound; that the Docker daemon is reachable; and whether `$REMOTE_PROJECT_DIR/.git` exists (informational). If anything is occupied → **STOP**, print findings, and ask:

> "Found an existing instance / occupied ports on `$REMOTE_SSH_HOST`. Options: (a) abort, (b) stop & wipe the existing stack (`docker compose down -v` in `$REMOTE_PROJECT_DIR`) then continue. What do you want me to do?"

Only proceed with explicit approval.

### Step 2 — Prepare repo on remote

Run `scripts/prepare-remote.sh`. Clones or `git fetch` + `reset --hard` + `clean -fdx` + `checkout $BRANCH` + `pull --ff-only`. Prints the resolved commit SHA so the user can verify what's under test.

### Step 3 — Start the docker compose stack

Run `scripts/start-remote.sh`. Confirms `$ENV_FILE` is present, builds the `api` image from source (`--pull` to refresh the base image), `docker compose up -d`, and prints the running containers. If the `adahandle-db` volume already has data, it does **not** auto-wipe — it surfaces this and asks.

### Step 4 — Open SSH tunnels

Run `scripts/open-tunnels.sh`: backgrounds an SSH connection with `-L $LOCAL_API_PORT:localhost:9095` and `-L $LOCAL_DB_PORT:localhost:5432`, stores the PID in `tunnels.pid`, and sanity-checks `http://localhost:$LOCAL_API_PORT/actuator/health`.

### Step 5 — Monitor sync to tip

**Preferred:** run `scripts/monitor-sync-progress.sh 1200 --until-tip` (or background it under a `Monitor`). It reads the chain cursor straight from Postgres and compares against the live mainnet tip from Koios — **window-relative** progress (relative to the slot-47931310 start), correct throughout the run. It exits 0 once window progress ≥ 99.95%. For a one-shot spot check use `scripts/check-sync-progress.sh`.

**Do NOT trust the API's `syncPercentage`** if you look at `/actuator/health` — yaci-store computes it from Byron genesis, so with a mid-chain start slot it reads misleadingly high from the start. The DB-cursor metric (window-relative) is the source of truth.

From slot 47931310 to tip is several hours. Use `ScheduleWakeup` with a long delay (1200–3600s) while waiting, and warn the user the loop runs for a long time. Print progress + ETA periodically.

### Step 6 — Check for parse errors (the hard-fork regression)

Once at tip, run `scripts/check-parse-errors.sh`. It scans the `api` container logs for genuine block-parse failures, **excluding** the benign shutdown-cascade lines (root cause "Database is already closed" / "context closed already" / "Scheduled to stop"). It also reports the cursor era.

- **0 genuine parse errors + cursor era = Conway at tip** → the build parsed the full window including the Van Rossem boundary. This is the headline pass.
- Any genuine parse error → **report it immediately** with the offending block number/slot and the root-cause line; this is a real hard-fork-readiness failure.

### Step 7 — REST spot-checks

Two scripts, both against the tunneled API:

**`scripts/run-golden-handles.sh`** — the deterministic golden check. Reads commonly-known mainnet handles from `golden-handles.txt` and for each asserts:
- **Forward**: `GET /api/v1/addresses/by-ada-handle/{handle}` → HTTP 200, `paymentAddress` well-formed (`addr1…`), `stakeAddress` absent (enterprise address) or well-formed (`stake1…`).
- **Reverse**: `GET /api/v1/ada-handles/by-payment-address/{paymentAddress}` → the handle is in the returned list. (Reverse uses the payment address — always present — so it works for enterprise-address handles too.)

Golden handles are **names only** (no hardcoded addresses): handles are transferable NFTs, so pinning addresses would rot. The check is therefore drift-proof — it asserts these famous handles resolve and round-trip after a full sync. Run at tip (a handle minted after a mid-sync cursor won't exist yet).

**`scripts/run-rest-spotchecks.sh`** — randomized coverage. Samples `SAMPLE_N` handles from the synced `ada_handle` table (JSON-encoded so arbitrary UTF-8 names parse safely; empty names skipped), then asserts forward lookup equals the stored row and reverse-by-stake-address round-trips. A DB-vs-API consistency check, so no false failures from drift. (For known-address assertions, set `EXPECTED_HANDLES` in `config.env` — optional.)

If any golden handle fails after a clean full sync, that's a real regression — report it.

### Step 8 — Report

Summarize: branch + commit tested; time spent (sync, total); final cursor (slot/block/era) vs tip; parse-error count (and first offenders if any); spot-check pass/fail counts (and first failures). Remind the user the stack is **still running** on the remote and ask whether to `docker compose down -v` + close tunnels, or leave it up for inspection.

## Handling failures

- **SSH connection lost mid-run.** Re-open tunnels and resume monitoring. Don't restart the stack.
- **Stack restarts mid-sync.** `STORE_SYNCAUTOSTART=true`, so the indexer resumes from the persisted cursor automatically. Keep monitoring.
- **Stuck health/no progress.** Pull `docker compose logs --tail=200 api` and `... db` remotely; surface the first ERROR line.

### Known issue: sync wedges on a dead relay IP ("Scheduled to stop")

Same root cause as filed upstream for yaci/yaci-store: the Cardano node hostname fronts a load-balanced pool of A-records; yaci-core's TCP client resolves to a **single** IP with no failover, and if it pins a now-unreachable member it loops forever.

**Symptom:** cursor stops advancing (`check-sync-progress.sh` shows no movement); API logs spam `io.netty.channel.ConnectTimeoutException: connection timed out ... :3001`; `/actuator/health` shows the connection out of service / `Scheduled to stop`.

**Mitigation (already configured):** `.env` sets `JAVA_TOOL_OPTIONS=... -Dnetworkaddress.cache.ttl=0 ...` so the JVM re-resolves DNS on every attempt and tends to pick a healthy IP.

**Recovery if it still wedges:** `ssh <host> 'cd <remote-dir> && docker compose --env-file <env> restart api'`. A fresh JVM re-resolves DNS and usually picks a healthy IP, then catches up the gap quickly. The Postgres cursor persists across the restart, so no re-sync.

## Files this skill owns

```
.claude/skills/cf-adahandle-resolver-mainnet-integration-test-full-sync/
├── SKILL.md                     # this file
├── config.env.example           # template, committed
├── config.env                   # actual config, gitignored
├── golden-handles.txt           # commonly-known mainnet handles for the golden check
├── tunnels.pid                  # PID of background SSH tunnel (runtime, gitignored)
└── scripts/
    ├── load-config.sh           # config loader (source first)
    ├── preflight-remote.sh      # remote occupancy check (read-only)
    ├── prepare-remote.sh        # clone / reset / checkout $BRANCH
    ├── start-remote.sh          # docker compose build + up (image from source)
    ├── open-tunnels.sh          # SSH port-forward 9095 + 5432
    ├── close-tunnels.sh         # kill background SSH tunnel
    ├── check-sync-progress.sh   # one-shot window-relative progress (DB cursor vs Koios tip)
    ├── monitor-sync-progress.sh # looped progress + ETA; --until-tip exits at ~100%
    ├── check-parse-errors.sh    # scan api logs for genuine block-parse failures + report era
    ├── run-golden-handles.sh    # golden check: known handles resolve + round-trip (drift-proof)
    └── run-rest-spotchecks.sh   # DB-vs-API consistency + reverse round-trip on sampled handles
```
