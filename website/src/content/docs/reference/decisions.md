---
title: Design decisions
description: The architecture decision records (ADRs) behind the resolver, including the ADA Handle rules it follows.
---

What counts as a handle, whom it resolves to and what a burn does are defined only by the code of
Kora Labs, who created ADA Handle. The resolver writes down the rules it follows,
and every other decision that shapes it, as **architecture decision records**. They live in the
repository under
[`docs/adr`](https://github.com/cardano-foundation/cf-adahandle-resolver/tree/main/docs/adr).

| ADR | Decision |
| --- | -------- |
| [1](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0001-record-architecture-decisions.md) | Record architecture decisions |
| [2](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0002-ada-handle-resolution-rules.md) | ADA Handle resolution rules, following Kora Labs' implementation |
| [3](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0003-virtual-sub-handles.md) | Virtual sub-handles resolve to the address in their datum |
| [4](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0004-ada-handle-policies.md) | Index both ADA Handle minting policies |
| [5](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0005-burned-handles.md) | Burning a handle's token removes the handle |
| [6](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0006-rollbacks-through-history.md) | Repair rollbacks from a history of ownership changes |
| [7](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0007-yaci-store-2-and-resync.md) | Yaci Store 2.x, and a full resync for 1.0.0 |
| [8](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0008-native-image.md) | A native image built with Oracle GraalVM, weekly from `main` |
| [9](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0009-testing-strategy.md) | Test on three levels: unit, devnet, and mainnet against Kora |
| [10](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0010-keep-jpa-writes.md) | Keep JPA for the indexer's writes: batched upserts didn't make the sync faster |
| [11](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0011-apply-changes-at-commit.md) | Apply handle changes at Yaci Store's `CommitEvent`, in chain order |
| [12](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0012-javascript-plugins.md) | JavaScript plugins, not Python |

## Where the resolver differs from handle.me

The resolver aims to answer exactly as Kora Labs' [api.handle.me](https://api.handle.me) does, and the
regression suite compares the two. These differences are known and deliberate, for now:

- **Datum hashes.** A virtual sub-handle's address is read from an inline datum only, not from a datum
  given by hash. No virtual sub-handle on mainnet uses a datum hash today.
- **Status codes.** handle.me answers `202` while it's catching up or rolling back, and `451` for
  legally restricted names. The resolver answers `200` and `404`.
- **Other chains.** handle.me can serve addresses on other blockchains from a handle's metadata. The
  resolver serves Cardano addresses only.
