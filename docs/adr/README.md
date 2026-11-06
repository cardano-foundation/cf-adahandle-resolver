# Architecture Decision Records

Decisions that shape the resolver, and why ([ADR 1](0001-record-architecture-decisions.md)). ADA Handles have no CIP;
[ADR 2](0002-ada-handle-resolution-rules.md) writes down the rules we follow instead. The tokens and datums those
rules work with are described in [ADA Handle tokens and datums](../handle-datums.md).

| ADR | Decision | Status |
|---|---|---|
| [1](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [2](0002-ada-handle-resolution-rules.md) | ADA Handle resolution rules, following Kora Labs' implementation | Accepted |
| [3](0003-virtual-sub-handles.md) | Virtual sub-handles resolve to the address in their datum | Accepted |
| [4](0004-ada-handle-policies.md) | Index both ADA Handle minting policies | Accepted |
| [5](0005-burned-handles.md) | Burning a handle's token removes the handle | Accepted (#78) |
| [6](0006-rollbacks-through-history.md) | Repair rollbacks from a history of ownership changes | Accepted |
| [7](0007-yaci-store-2-and-resync.md) | Yaci Store 2.x, and a full resync for 1.0.0 | Accepted |
| [8](0008-native-image.md) | A native image built with Oracle GraalVM, checked weekly, published with releases | Accepted |
| [9](0009-testing-strategy.md) | Test on three levels: unit, devnet, mainnet against Kora | Accepted |
| [10](0010-keep-jpa-writes.md) | Keep JPA for the indexer's writes: batched upserts didn't make the sync faster | Accepted |
| [11](0011-apply-changes-at-commit.md) | Apply handle changes at Yaci Store's CommitEvent, in chain order | Accepted |
| [12](0012-sync-status-headers.md) | Every API response says how current its data is, in X-Sync-* headers | Accepted |

Guidelines shared by the Cardano Foundation's Java services, such as native image builds, testing layers, observability,
packaging, CI and releases (including release candidates `X.Y.Z-rcN`), live in the Cardano Foundation's internal
`cf-java-nfrs` repository and are not repeated here. These ADRs record what is specific to the resolver, and where it
departs from those guidelines.

To add one: copy the structure of an existing ADR (status, date, context, decision, consequences, references), give it
the next number, and add it to this table. To change a decision, add an ADR that supersedes the old one.
