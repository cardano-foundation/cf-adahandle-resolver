---
title: Scaling the API
description: Run many read-only API replicas against a single indexer.
---

Each resolver instance does two things: it **indexes** the chain and it **serves** the REST API. The
API is stateless and cheap, but the indexer should run exactly once per database.

To scale out, run one indexer and as many API replicas as you need, all pointing at the same
PostgreSQL database:

```text
              ┌──────────────────────────┐
  relay ────▶ │ resolver (indexer + API) │ ───┐
              └──────────────────────────┘    │ writes
                                              ▼
                                        ┌────────────┐
              ┌──────────────────────┐  │ PostgreSQL │
  clients ──▶ │ resolver × N         │◀─┤            │
              │ (disable-indexer)    │  └────────────┘
              └──────────────────────┘   reads
```

Start the replicas with the `disable-indexer` profile. This sets `store.sync-auto-start=false`, so
these instances never connect to a node:

```sh
SPRING_ACTIVE_PROFILES=mainnet,postgres,disable-indexer \
DB_URL=jdbc:postgresql://db:5432/adahandle \
java -jar target/cf-adahandle-resolver-*.jar
```

:::note
`disable-indexer` only works with a shared database, so use `postgres`. With an embedded H2 database
each replica would have its own empty store.
:::
