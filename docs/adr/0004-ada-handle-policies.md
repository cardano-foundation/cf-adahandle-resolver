# 4. Index both ADA Handle minting policies

- Status: Accepted
- Date: 2026-11-03

## Context

The resolver indexed one minting policy, `f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a`. Kora Labs mint
under two, the same on mainnet, preprod and preview (`kora-labs-common` `src/handles/policies.ts`):

| Policy | Use |
|---|---|
| `f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a` | the original ADA Handle policy |
| `6c32db33a422e0bc2cb535bb850b5a6e9a9572222056d6ddc9cbc26e` | DeMi: decentralized minting, on mainnet since April 2025 |

The 38 DeMi handles on mainnet (`demiking`, `first_demi`, …) returned 404, and none of our checks noticed, because they
all sampled handles the resolver already had.

## Decision

- Index the tokens of both policies, with the same rules ([ADR 2](0002-ada-handle-resolution-rules.md)). No switch per
  network is needed: the policies are the same everywhere.
- `adahandle.policy-ids` (`ADAHANDLE_POLICY_IDS`) lists the policies and defaults to both. Only the integration tests
  change it, to index handles they mint on a devnet; a non-default value logs a warning.

## Consequences

- A new Kora policy means a new default here (and a resync for its earlier handles).
- The regression check `check-coverage` starts from the chain (Koios) instead of the database: it lists every policy's
  handle tokens and checks the resolver knows each, so a missed policy shows up.

## References

- #71 (policies), #72 (`check-coverage`); `mapper/AdaHandlePolicies.java`, `config/AdaHandlePoliciesConfig.java`.
