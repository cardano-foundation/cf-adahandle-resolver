---
title: Monitoring
description: Health probes, sync status and Prometheus metrics.
---

The resolver uses Spring Boot Actuator under `/actuator`. Yaci Store, the indexer it is built on, adds two health
checks and a set of Prometheus metrics about the sync.

## Health

| Endpoint                     | Checks                                    | `200` when                                |
| ---------------------------- | ----------------------------------------- | ----------------------------------------- |
| `/actuator/health/liveness`  | `livenessState`, `nodeHealth`             | blocks are arriving from the node         |
| `/actuator/health/readiness` | `readinessState`, `nodeSync`, `db`        | the sync has reached the tip              |
| `/actuator/health/startup`   | `db`, `nodeHealth`                        | the database is up and the node connected |
| `/actuator/health`           | all of the above, plus disk space         | everything is `UP`                        |

When a check fails, the endpoint returns `503`. Every response includes the details:

```json
{
  "status": "OUT_OF_SERVICE",
  "components": {
    "db": { "status": "UP" },
    "nodeSync": {
      "status": "OUT_OF_SERVICE",
      "details": { "connectionAlive": true, "receivingBlocks": true, "syncStatus": "Syncing", "syncPercentage": "47.05%" }
    },
    "readinessState": { "status": "UP" }
  }
}
```

The two Yaci Store checks:

- **`nodeHealth`** reports the connection to the node:
  - `UP`: `Connected`;
  - `OUT_OF_SERVICE`: `Not receiving blocks` (no block for 5 minutes) or `Scheduled to stop`;
  - `DOWN`: `Connection lost or sync error`.
- **`nodeSync`** reports the sync:
  - `UP`: `Synced`, within 10 blocks of the tip;
  - `OUT_OF_SERVICE`: `Syncing`, as well as the `nodeHealth` problems.

  As in [`/api/v1/sync/status`](../../reference/api/#sync-status), its `syncPercentage` counts from the start of the
  chain, so it reads high from the beginning.

:::caution[Readiness during the first sync]
A fresh instance is not ready until the sync reaches the tip, which takes about 6.5 hours on mainnet. Until then,
its answers would be out of date, so Kubernetes sends it no traffic. Plan for it on a first deployment. With a
persistent database, a restarted pod picks up where it stopped and is ready again in minutes.
:::

### Kubernetes

The [Helm chart](../deployment/#kubernetes-with-helm) points each probe at its group:

| Probe     | Path                         | Timing                                                |
| --------- | ---------------------------- | ----------------------------------------------------- |
| startup   | `/actuator/health/startup`   | `startupProbe.*` values: 60 s delay, then every 20 s  |
| liveness  | `/actuator/health/liveness`  | every 30 s, restart after 3 failures                  |
| readiness | `/actuator/health/readiness` | every 10 s                                            |

If the node connection gets stuck, liveness fails and Kubernetes restarts the pod. The new JVM reconnects, often to
another relay behind the same host name, and continues from the last block it stored.

### Docker Compose

The `api` service has a healthcheck on `/actuator/health/liveness` every 30 s, so `docker compose ps` shows
`healthy` or `unhealthy`. Docker doesn't restart an unhealthy container on its own.

### API-only replicas

Instances with the [`disable-indexer`](../scaling/) profile don't connect to a node, so `nodeHealth` and `nodeSync`
report `UNKNOWN` there and don't fail their probes. To tell whether the shared database is up to date, watch the
indexer instance.

### Threshold

`nodeHealth` and `nodeSync` report `Not receiving blocks` after 300 s without a block. Set it with
`STORE_BLOCK_RECEIVE_DELAY_SECONDS`. Yaci Store's own default is 120 s, but mainnet makes a block every 20 s on
average, so normal 2-minute gaps happen several times a day. Each would fail liveness and restart the pod. A 5-minute
gap happens about once in two years.

## Build information

`/actuator/info` says which build is running: the version, the build time and the git commit it was built from.

```sh
curl -s localhost:9095/actuator/info
```

```json
{"git":{"commit":{"id":"6088cac", …}},"build":{"version":"1.0.0-rc3","name":"ADA Handle Resolver", …}}
```

## Metrics

`/actuator/prometheus` serves metrics in the Prometheus format, and `/actuator/metrics` serves the same as JSON. Every
metric carries the label `application="cf-adahandle-resolver"`.

### Handles

| Metric                               | Type    | Meaning                                                                  |
| ------------------------------------ | ------- | ------------------------------------------------------------------------ |
| `adahandle_handles{type}`            | gauge   | Handles that exist now, as the API resolves them                         |
| `adahandle_mints_total{type}`        | counter | Handle tokens minted                                                     |
| `adahandle_burns_total{type}`        | counter | Handle tokens burned                                                     |
| `adahandle_history_rows_total`       | counter | Handle states written to the history, one per handle and block           |
| `adahandle_rollbacks_total{outcome}` | counter | Rollbacks: `noop` if nothing was stored after the rollback point, else `applied` |
| `adahandle_rollback_handles_total`   | counter | Handles that rollbacks restored to an earlier owner, or removed          |

`type` is `handle` or `subhandle` (a name with `@`); on mainnet, about 262,000 handles and 3,800 sub-handles.

- **Mints and burns:** they count tokens. A legacy handle moved to CIP-68 counts as a mint and a burn, while
  `adahandle_handles` stays the same.
- **The counters** start at zero with each process, and count during the first sync too, from the first ADA Handle
  block. Changes from blocks that are rolled back before they're stored aren't counted. Use `rate()` or
  `increase()` on them.
- **The gauge** counts the database at most every 30 s. [API-only replicas](../scaling/) report it too, but their
  counters stay at zero.

```promql
# Handles minted in the last day
sum(increase(adahandle_mints_total[1d]))
# Rollbacks that changed something, per hour
increase(adahandle_rollbacks_total{outcome="applied"}[1h])
```

### Sync

The sync metrics from Yaci Store:

| Metric                                | Meaning                                                    |
| ------------------------------------- | ---------------------------------------------------------- |
| `yaci_store_current_block`            | Block number the indexer has reached                       |
| `yaci_store_current_slot`             | Slot of that block                                         |
| `yaci_store_current_epoch`            | Epoch of that block                                        |
| `yaci_store_current_era`              | Era number, `7` for Conway                                 |
| `yaci_store_current_block_time`       | Time of that block, in milliseconds                        |
| `yaci_store_last_received_block_time` | When the last block arrived from the node, in milliseconds |
| `yaci_store_sync_mode`                | `1` once the indexer follows the tip, `0` while catching up |

### Others

The standard Spring Boot metrics:
- the JVM: memory, GC, threads (see [Memory](#memory) for the native image);
- the HikariCP connection pool;
- `http_server_requests_seconds`, timings for each API endpoint;
- `spring_data_repository_invocations_seconds`, timings for each database query.

### Memory

The JVM image reports `jvm_memory_used_bytes`, `jvm_memory_committed_bytes` and `jvm_memory_max_bytes` per memory pool
(G1 Eden, G1 Old Gen, and so on). The native image has no such pools, so it reports the heap as a whole, with the labels
`area="heap", id="heap"`. To chart the heap on both images, sum over `area="heap"`:

```promql
sum by (instance) (jvm_memory_used_bytes{area="heap"})
```

The native image reports no GC pause metrics.

Both images also report `process_memory_rss_bytes` on Linux: the memory the process actually holds in RAM (its
resident set size), heap and everything else. This is the number to compare with a container's memory limit.

### Alerts

Two examples:

```yaml
groups:
  - name: adahandle-resolver
    rules:
      - alert: AdaHandleResolverNoBlocks
        expr: time() - yaci_store_last_received_block_time{application="cf-adahandle-resolver"} / 1000 > 600
        for: 5m
        annotations:
          summary: No block from the node for 10 minutes
      - alert: AdaHandleResolverBehindTip
        expr: time() - yaci_store_current_block_time{application="cf-adahandle-resolver"} / 1000 > 600
        for: 15m
        annotations:
          summary: The indexer is more than 10 minutes behind the chain
```

The second fires during the first sync too, so silence it there.
