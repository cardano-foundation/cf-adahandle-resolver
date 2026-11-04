# 11. Apply handle changes at Yaci Store's CommitEvent, in chain order

- Status: Accepted (implemented in #78)
- Date: 2026-11-04

## Context

The resolver learns about handles from Yaci Store's block events: `AddressUtxoEvent` for outputs and `MintBurnEvent`
for burns. During the initial sync, Yaci Store processes blocks in batches of 100, split into partitions that run in
parallel (`store.executor.blocks-batch-size`, `blocks-partition-size`). Its events therefore arrive in no particular
order: an output of a later block can arrive before an output of an earlier one. A handle changes owner many times, so
writing each change as it arrives can leave an older owner in place, or bring back a burned handle.

Yaci Store doesn't deliver events in chain order, and doesn't expose a block's transaction order on these events
(bloxbean/yaci-store#1320).

Yaci Store's own modules follow a different guideline. They save data in parallel in the normal listeners, and keep
only the delta that can't be processed in parallel for `CommitEvent`. For example, `address_utxo` is saved in parallel,
and only the pending pointer addresses are resolved at the commit. They also avoid database lookups on this hot path.
For Yaci Store, whose UTxO tables are huge, this is what makes the sync fast.

Yaci Store publishes two events at the end of each batch (100 blocks during the initial sync, one block at the tip):

| | `PreCommitEvent` | `CommitEvent` |
|---|---|---|
| **Order** | First | Immediately after, on the same thread |
| **Carries** | The metadata of the batch's last block | The same metadata, plus the batch's blocks with their transactions |
| **Used by Yaci Store for** | Preparing data that commit handlers use: adapot's UTxO amounts, account balances | Saving a module's remaining delta |

Both are published after the parallel processing has finished and before the cursor is saved
(`ShelleyBlockEventPublisher`, `ByronBlockEventPublisher` in 2.0.7). Both are in the `events.internal` package. A
listener sees the same data at either.

## Decision

- The block listeners don't write. They decode each handle change in memory (an owner for each output carrying a
  handle token, a burn for each burned one) and add it to a list of pending changes. They do no database lookups.
- On `CommitEvent`, the pending changes are sorted by slot and written in one transaction: to the history
  ([ADR 6](0006-rollbacks-through-history.md)), and each handle's last state to `ada_handle`. The order of changes within a block is decided by the burned token ([ADR 5](0005-burned-handles.md)).
- The commit runs on `CommitEvent`, not `PreCommitEvent`. `PreCommitEvent` exists so Yaci Store's modules can prepare
  data before the commit handlers run. Writing a module's own changes belongs on `CommitEvent`.
- A rollback that arrives before the commit drops the pending changes from the abandoned blocks.

This goes against Yaci Store's guideline of saving in parallel, on purpose:
- **Volume:** handle changes are a few thousand rows per batch, at most about 1,900 a second during the heaviest
  minting. [ADR 10](0010-keep-jpa-writes.md)'s benchmark shows the writes don't limit the sync.
- **Order:** deferring every change to the commit is what puts the changes in chain order, which the parallel listeners
  can't do.

## Consequences

- The pending changes are held in memory until the commit: at most one batch of 100 blocks.
- The history and `ada_handle` are written in chain order, whatever order the events arrive in.
- If handle data ever grows enough for the commit to show up in a profile, the guideline applies: decode and save in
  parallel what doesn't depend on order, and keep only the ordering at the commit.
- If Yaci Store delivers events in chain order, or exposes the transaction index (bloxbean/yaci-store#1320), the
  ordering logic can be simplified. The commit-time write can stay.

## References

- #78 (pending changes applied at the commit), #86 (order within a block); `storage/AdaHandleProcessor.java`.
- Yaci Store 2.0.7: `components/core/.../publisher/ShelleyBlockEventPublisher.java`; `aggregates/adapot/.../UtxoPotProcessor.java`
  (a `PreCommitEvent` user).
- A Yaci Store maintainer's review comment explaining the parallel-saving guideline and the two events.
