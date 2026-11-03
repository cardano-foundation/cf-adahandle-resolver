# 6. Repair rollbacks from a history of ownership changes

- Status: Accepted
- Date: 2026-11-03

## Context

The chain rolls back: forks are abandoned, and Yaci Store publishes a rollback to its last block on every start. Before
#54, rollbacks left wrong owners behind and, on every restart, wiped CIP-68 handles.

## Decision

- Every ownership change is a row of `ada_handle_history_item`, keyed by `(name, slot)`; a burn is a row too
  ([ADR 5](0005-burned-handles.md)). `ada_handle` holds each handle's current owner, equal to its latest history row.
- On a rollback to slot S: find the handles with history after S, delete that history, then set each of them to its
  latest remaining row, or delete it if it has none or that row is a burn. Only the affected handles are touched.
- Outputs (and burns) are cached per block and written when Yaci Store commits the batch, in slot order; a rollback that
  arrives first drops the cached blocks after S.
- History is kept forever, so a rollback of any depth can be repaired.

## Consequences

- Rollbacks are cheap (proportional to the handles they affect) and safe on restart.
- The history grows with every ownership change (about 15 M rows on mainnet, a few GB); pruning rows deeper than the
  security parameter is possible later.
- Tested: unit tests through the real services on H2, a restart on the devnet in the integration tests, and on mainnet
  (node rollbacks, plus 20 CIP-68 handles across a restart in the regression suite).

## References

- #54 (with #58), #66; `service/AdaHandleHistoryService.java#rollbackToSlot`, `storage/AdaHandleProcessor.java`.
