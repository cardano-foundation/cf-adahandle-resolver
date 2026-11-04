# 10. Keep JPA for the indexer's writes: batched upserts didn't make the sync faster

- Status: Accepted
- Date: 2026-11-03

## Context

The indexer writes its changes when Yaci Store commits a batch of blocks (100 during the initial sync, 1 at the tip):
one history row per ownership change or burn, and each handle's latest state in `ada_handle`
([ADR 6](0006-rollbacks-through-history.md)). It writes them with Spring Data JPA's `saveAll`.

Both tables have assigned keys (`name`; `name, slot`), so JPA can't tell a new row from an existing one: each `save()`
reads the row before writing it, and without Hibernate batching each write is its own statement. A full mainnet sync
writes about 15 million history rows, so this looked like the obvious thing to optimise.

## The alternative we measured

Write each commit as batched upserts instead: `INSERT … ON CONFLICT … DO UPDATE` with jOOQ (already in the app through
Yaci Store), one JDBC batch per table, rows with the same key collapsed first; JPA kept for reads and rollback repairs.
It passed the unit tests (H2) and the devnet integration tests (PostgreSQL).

Benchmark, 2026-11-03, on the mainnet test host (a KVM virtual machine, 31 GB of RAM, otherwise idle): both builds
synced mainnet **at the same time**, from the first ADA Handle mint (slot 47,931,310), each with its own fresh
PostgreSQL 17 and `-Xmx4g`, from the same relay, for 30 minutes.

- A: JPA `saveAll`, the current code (#78 at `0dc5b68`);
- B: batched upserts (`78cb1bc`, not merged).

| Minutes | A: slot | B: slot | A: history rows | B: history rows |
|---|---|---|---|---|
| 5 | 49,910,234 | 49,889,414 | 178,839 | 176,997 |
| 10 | 51,349,661 | 51,324,264 | 292,806 | 291,250 |
| 15 | 52,728,184 | 52,694,037 | 361,820 | 360,405 |
| 20 | 54,205,439 | 54,174,838 | 417,240 | 416,324 |
| 25 | 55,691,420 | 55,680,880 | 527,464 | 505,459 |
| 30 | 56,399,670 | 56,373,628 | 1,097,422 | 1,085,734 |

The two kept the same pace throughout, A marginally ahead (less than 0.1 %, noise), including the last 5 minutes, the
heaviest stretch of handle minting (about 570,000 history rows, some 1,900 per second). Neither logged an error.

## Decision

Keep JPA `saveAll` for the indexer's writes. The database writes are not what limits the sync speed: Yaci Store's
block fetching and processing are. The upserts would add about 60 lines of SQL-building code, a second way of writing
the same tables, and no correctness gain (JPA's merge is already harmless if a block is delivered twice).

## Consequences

- One way of writing the tables, through the JPA entities.
- If the sync speed needs work, look at Yaci Store's processing first (`store.executor.blocks-batch-size`,
  `blocks-partition-size`, the node connection), and measure the same way: two builds side by side, from the same
  slot, on the same host.
- Revisit if the write path changes (for example, many more rows per change) or if a profile shows the commit as the
  bottleneck.
- All handle changes are written in the `CommitEvent` handler, none in the parallel block listeners. Yaci Store's own
  guideline is the opposite: save in parallel in the normal listeners, and keep only the delta that can't be processed in
  parallel for `CommitEvent`. For example, `address_utxo` is saved in parallel, and only the pending pointer addresses are
  resolved at the commit. That matters for Yaci Store, whose UTxO tables are huge. Here it doesn't: handle changes are a
  few thousand rows per batch, at most about 1,900 a second during the heaviest minting, and the benchmark above shows
  the writes don't limit the sync. Deferring every change to the commit is also what puts them in chain order, which
  the parallel listeners can't do (bloxbean/yaci-store#1320). The listeners do no database lookups on the hot path.
- The commit runs on `CommitEvent`, not `PreCommitEvent`. Both are published once per batch, right after the parallel
  processing and before the cursor is saved, and a listener sees the same data at either. `PreCommitEvent` comes first,
  so that Yaci Store's modules (adapot, account balances) can prepare data the commit handlers use. Both are in Yaci
  Store's `events.internal` package; `CommitEvent` is the one meant for writing a module's own delta.
