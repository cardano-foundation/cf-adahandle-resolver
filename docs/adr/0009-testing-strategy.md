# 9. Test on three levels: unit, devnet, mainnet against Kora

- Status: Accepted
- Date: 2026-11-03

## Context

The resolver is only right if it answers like Kora Labs' API for real mainnet handles
([ADR 2](0002-ada-handle-resolution-rules.md)), and that depends on chain data unit tests can't reproduce: datums,
rollbacks, restarts, years of history. Bugs we found (CIP-68 handles lost on restart, virtual sub-handles, DeMi,
burns) were only visible on a synced mainnet instance or against Kora.

## Decision

1. **Unit tests** (`mvn test`, H2): decoding of every handle kind, policies, burns, and rollbacks through the real
   processor and services.
2. **Integration tests** on every push and pull request (`.github/workflows/integration-tests.yaml`): the resolver syncs
   a local [Yaci DevKit](https://github.com/bloxbean/yaci-devkit) devnet into PostgreSQL. The tests mint legacy, CIP-68,
   NFT and virtual sub-handles under two test policies, transfer and burn them, restart the resolver, and check the API.
   The devkit's own Yaci Store is checked to follow the devnet before the tests start (restarted once if not).
3. **Mainnet regression** (Claude Code skills in `.claude/skills/`), before each release: a full sync from the first
   ADA Handle mint, then checks against [api.handle.me](https://api.handle.me) and the chain:
   - deployment, API surface, sync health (0 block-parse errors) and database invariants;
   - golden handles, at least one of every kind, picked from Kora's API and compared with it;
   - a random sample of handles and NFT sub-handles, and every sub-handle, compared with Kora;
   - coverage: every policy's handle tokens, listed from the chain (Koios), must be known;
   - an API restart keeps CIP-68 handles.

## Consequences

- Kora's API is an external dependency of the regression suite (rate limits, lag); its checks re-check differences
  before failing.
- A full mainnet run takes about 7 to 12 hours; it runs unattended on a test host.

## References

- The layers follow the Cardano Foundation's internal testing guideline (`cf-java-nfrs`, NFR-03); this ADR records
  what each layer checks for ADA Handles.
- #64, #65, #72, #73, #77, #79; `src/test/java/.../it/`, `.claude/skills/cf-adahandle-resolver-regression/`.
