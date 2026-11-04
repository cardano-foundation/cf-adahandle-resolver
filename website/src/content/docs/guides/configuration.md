---
title: Configuration
description: Spring profiles and environment variables that control the network, database and node connection.
---

All settings come from Spring profiles and environment variables. You don't need to edit config
files.

## Profiles

Set profiles with `SPRING_ACTIVE_PROFILES` as a comma-separated list. Combine one **network** profile
with one **database** profile, and add the optional profiles if you need them.

| Profile           | Kind     | Effect                                                                       |
| ----------------- | -------- | ---------------------------------------------------------------------------- |
| `mainnet`         | network  | Mainnet, protocol magic `764824073`. These are also the built-in defaults.   |
| `preprod`         | network  | Preprod, protocol magic `1`.                                                 |
| `preview`         | network  | Preview, protocol magic `2`.                                                 |
| `devnet`          | network  | A local Yaci DevKit devnet, protocol magic `42`, synced from slot `0`.       |
| `h2`              | database | Embedded H2 database, plus the H2 web console.                               |
| `postgres`        | database | PostgreSQL through `DB_URL`.                                                 |
| `local-node`      | optional | Sync from your own node over node-to-client instead of a remote relay.       |
| `disable-indexer` | optional | Serve the API only, without syncing. See [Scaling the API](../scaling/).     |

The default is `mainnet,h2`.

## Environment variables

### Server and database

| Variable                 | Description                                  | Default             |
| ------------------------ | -------------------------------------------- | ------------------- |
| `SPRING_ACTIVE_PROFILES` | Active profiles                              | `mainnet,h2`        |
| `PORT`                   | HTTP port                                    | `9095`              |
| `DB_URL`                 | JDBC URL                                     | `jdbc:h2:./data/db` |
| `DB_USERNAME`            | Database user                                | `sa`                |
| `DB_PASSWORD`            | Database password                            | `password`          |
| `DB_DRIVER`              | JDBC driver class (`postgres` profile only)  | `org.postgresql.Driver` |

With the `postgres` profile and no `DB_URL`, the resolver connects to
`jdbc:postgresql://localhost:5432/adahandle`.

### Chain sync

| Variable                                   | Description                                    | Mainnet default            |
| ------------------------------------------ | ---------------------------------------------- | -------------------------- |
| `REMOTE_NODE_URL`                          | Relay host to sync from                        | `backbone.cardano.iog.io`  |
| `REMOTE_NODE_PORT`                         | Relay port                                     | `3001`                     |
| `PROTOCOL_MAGIC`                           | Network magic (`preprod` and `preview` only)   | network-specific           |
| `YACI_STORE_CARDANO_SYNC_START_SLOT`       | Slot to start syncing from                     | `47931310`                 |
| `YACI_STORE_CARDANO_SYNC_START_BLOCK_HASH` | Hash of the block at that slot                 | `89f934…877b`              |
| `STORE_SYNCAUTOSTART`                      | Start syncing on boot                          | `true`                     |
| `STORE_BLOCK_RECEIVE_DELAY_SECONDS`        | Seconds without a block before the health checks fail ([Monitoring](../monitoring/#threshold)) | `300` |

The preprod and preview profiles default to the public `*-node.world.dev.cardano.org` relays
on ports `30000` and `30002`.

:::caution
Only change the start slot to a point **before** the first handle mint you care about. The resolver
reads handles only from blocks it has synced, so it won't know about handles minted before that slot.
:::

### ADA Handle policies

| Variable               | Description                                            | Default                  |
| ---------------------- | ------------------------------------------------------ | ------------------------ |
| `ADAHANDLE_POLICY_IDS` | Comma-separated minting policies indexed as handles    | Both ADA Handle policies |

The default covers the original ADA Handle policy and DeMi's, which are the same on every network.
Only change it for tests, for example to index handles you mint yourself on a devnet.

### Local node

With the `local-node` profile, set either the socket path or the host and port of your node:

| Variable                 | Description                         |
| ------------------------ | ----------------------------------- |
| `LOCAL_NODE_SOCKET_PATH` | Path to the node's Unix socket      |
| `LOCAL_NODE_HOST`        | Node-to-client host (e.g. socat)    |
| `LOCAL_NODE_PORT`        | Node-to-client port                 |

### JVM

The container image starts with a fixed `java -jar` entrypoint, so pass JVM flags through
`JAVA_TOOL_OPTIONS`. `JAVA_OPTS` is ignored. The native image is the exception: it takes JVM flags
such as `-Xmx2g` on the command line instead. Recommended for mainnet:

```sh
JAVA_TOOL_OPTIONS="-Xmx4g -Dnetworkaddress.cache.ttl=0 -Dnetworkaddress.cache.negative.ttl=0"
```

Disabling the JVM DNS cache makes the resolver pick a fresh IP each time it reconnects to a
round-robin relay pool, so it doesn't keep retrying a relay that has gone away.

## Plugins

Yaci Store's plugin system lets you run your own scripts, in JavaScript, MVEL or SpEL, inside the resolver, for
example a webhook for handle mints and burns. It is off by default (`STORE_PLUGINS_ENABLED`), and so is its monitoring API
(`STORE_PLUGINS_API_ENABLED`). See [Plugins](../plugins/).

## CORS

All `/api/**` routes accept cross-origin requests from any origin, so a browser dApp can call the
resolver directly.
