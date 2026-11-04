# 5. Burning a handle's token removes the handle

- Status: Accepted (implemented in #78)
- Date: 2026-11-03

## Context

When a handle's token was burned, the resolver kept resolving it to its last holder. Kora removes it
(`ogmios.service.ts::processBlock`: a mint of quantity −1 under a handle policy calls `removeHandle` for legacy,
label-222 and label-0 tokens).

On mainnet this was the only difference between our handle set and Kora's: 266,044 live handle names on chain (Koios),
which is Kora's total, against our 266,063. The 19 extra were exactly the handles burned and never minted again
(ours 200, Kora 404, e.g. `ap@adaprotocol`, `brockcruess`).

## Decision

- Burning the token of any handle kind (legacy, label 222 including NFT sub-handles, label 0) removes the handle.
  Burning a reference (100) or settings (1) token removes nothing.
- Burns come from Yaci Store's `MintBurnEvent` (negative quantities). Like outputs, they are cached and applied when the
  batch commits, in slot order: during the initial sync blocks are processed in parallel, so applying a burn on arrival
  could be undone by an earlier output written afterwards.
- Within a block, Yaci Store doesn't say in which order the transactions came, but the burned token does. An output
  carrying the burned token came before the burn, because a burned token no longer exists. An output carrying another
  token of the same name came after it. So a handle transferred and burned in one block is removed, and a handle whose
  token is burned and minted again as another token in one transaction lives on. That is how legacy handles were
  migrated to CIP-68: one transaction burns the legacy token and mints the label-222 token (415 handles on mainnet,
  e.g. `kanemiller`). Kora Labs applies a transaction's burns before its outputs, which gives the same result. The
  history keeps one row per handle and block: its state at the end of the block.
- A burn is recorded as a history row with `burned = true` and no addresses, so rollbacks handle it like any change
  ([ADR 6](0006-rollbacks-through-history.md)): rolling back past a burn restores the handle. A handle minted again later
  resolves to its new holder.

## Consequences

- Existing databases need a resync to drop handles burned before this change (1.0.0 requires one anyway).
- The regression checks treat a handle the resolver knows and Kora doesn't as a failure.
- `check-burned` checks the other direction: every handle the resolver removed as burned must be gone on Kora too.

## References

- #78; `storage/AdaHandleProcessor.java`, `mapper/AdaHandleMapper.java#burnedHandleName`, migration
  `V3__history_burns.sql`.
