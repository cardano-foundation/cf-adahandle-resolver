---
title: How indexing works
description: Which tokens are handles, how the resolver turns blocks into a handle → address table, and how it stays correct across burns and rollbacks.
---

The resolver is a Spring Boot application built on
[Yaci Store](https://github.com/bloxbean/yaci-store). Yaci Store handles the node connection, block
parsing and UTxO extraction. The resolver adds one processor that picks out ADA Handles, plus the
rules for what a handle resolves to.

ADA Handles have no CIP. The rules below follow the implementation of Kora Labs, who created ADA Handle
and run [api.handle.me](https://api.handle.me), and the regression suite checks the resolver against
their API. The reasoning behind each rule is recorded in the
[design decisions](../decisions/).

## Which tokens are handles

A handle is a token of one of the two ADA Handle minting policies. Both are the same on mainnet,
preprod and preview:

| Policy                                                     | Mints                                          |
| ---------------------------------------------------------- | ---------------------------------------------- |
| `f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a` | The original ADA Handle policy                 |
| `6c32db33a422e0bc2cb535bb850b5a6e9a9572222056d6ddc9cbc26e` | DeMi, decentralized minting (since April 2025) |

The asset name says which kind of handle it is. Labels follow
[CIP-67](https://cips.cardano.org/cip/CIP-0067): a 4-byte prefix with a checksum.

| Asset name                               | Handle kind                                               | Resolves to                                      |
| ---------------------------------------- | --------------------------------------------------------- | ------------------------------------------------ |
| No label: the name itself (`alice`)      | Legacy handle (CIP-25)                                    | The address holding the token                    |
| Label 222 (`000de140` + name)            | CIP-68 handle, or an NFT sub-handle when it's `sub@root`  | The address holding the token                    |
| Label 0 (`00000000` + `sub@root`)        | Virtual sub-handle                                        | The address in its datum                         |

Tokens with other labels are not handles: 100 (the CIP-68 reference token that holds the metadata),
1 (a root handle's sub-handle settings), 002 and 444.

### Virtual sub-handles

A virtual sub-handle is held by a Handle script, not by its owner, so the address holding the token
says nothing about who it belongs to. The owner is in the token's inline datum, at
`extra.resolved_addresses.ada`, and that's the address the resolver serves, as handle.me does. When
the token moves with a new datum, the sub-handle follows the new address.

:::note
Datums given only by hash, with the datum itself in the transaction's witnesses, aren't read yet. No
virtual sub-handle on mainnet uses one today.
:::

## From block to handle

1. **Sync.** Yaci Store follows the chain from the configured start slot, the block of the first
   ADA Handle mint. For every block it emits the transaction outputs (`AddressUtxoEvent`) and the
   mints and burns (`MintBurnEvent`).
2. **Filter and decode.** The processor keeps the outputs that carry a handle token and decodes each
   handle's name and address, using the rules above. A burn (a negative mint) of a handle token is
   recorded as a burn of that handle.
3. **Collect.** The changes are held in memory until Yaci Store commits. During the initial sync a
   commit covers a batch of 100 blocks. At the tip there's a commit after every block.
4. **Order.** Yaci Store processes the blocks of a batch in parallel, so their events arrive in no
   particular order. At the commit, the resolver sorts the changes by slot and works out each
   handle's state at the end of each block. Within a block, the burned token tells the order: an
   output carrying the burned token came before the burn, and an output carrying another token of
   the same name came after it. That's how legacy handles were migrated to CIP-68: one transaction
   burns the legacy token and mints the CIP-68 token, and the handle lives on.
5. **Write.** In one transaction, each handle's state at the end of each block is appended to the
   history, and its last state is written to `ada_handle`. A handle whose last state is burned is
   deleted.

## Burned handles

Burning a handle's token removes the handle, for every kind of handle, as on handle.me. It no longer
resolves (`404`) and drops out of the reverse lookups. If the same name is minted again later, it
resolves to its new holder. Burning a reference or settings token removes nothing.

## Storage

The schema is managed by Flyway and has two tables:

| Table                     | Key            | Holds                                                                  |
| ------------------------- | -------------- | ---------------------------------------------------------------------- |
| `ada_handle`              | `name`         | The current state: `payment_address`, `stake_address`                  |
| `ada_handle_history_item` | `(name, slot)` | Each handle's state at the end of every block that changed it, including burns (`burned`) |

`ada_handle` is indexed on both address columns, which serves the reverse lookups. A full mainnet index
holds about 266,000 handles and some 15 million history rows.

## Rollbacks

Cardano can roll back a few blocks when forks are resolved. Yaci Store also sends a rollback to the
last committed block every time it starts. When Yaci Store emits a `RollbackEvent` to slot _S_, the
resolver:

1. Drops the changes from slots after _S_ that haven't been committed yet.
2. Finds the handles that changed after _S_, then deletes those history rows.
3. For each affected handle, restores the latest remaining history row as its current state. If no
   row is left, because the handle was first minted on the abandoned fork, or if that row is a burn,
   it deletes the handle. A rolled-back burn therefore brings the handle back.

The repair touches only the handles that changed on the abandoned fork, so it stays fast on a
mainnet-sized database. A rollback to the current tip deletes nothing and returns immediately.
