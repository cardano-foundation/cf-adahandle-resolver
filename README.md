# ADA Handle Resolver

<p align="left">
<img alt="Tests" src="https://github.com/cardano-foundation/cf-adahandle-resolver/actions/workflows/tests.yaml/badge.svg?branch=main" />
<img alt="Coverage" src="https://github.com/cardano-foundation/cf-adahandle-resolver/blob/gh-pages/badges/jacoco.svg?raw=true" />
<img alt="Release" src="https://github.com/cardano-foundation/cf-adahandle-resolver/actions/workflows/release.yaml/badge.svg?branch=main" />
<img alt="Publish" src="https://github.com/cardano-foundation/cf-adahandle-resolver/actions/workflows/publish.yaml/badge.svg?branch=main" />
<a href="https://conventionalcommits.org"><img alt="conventionalcommits" src="https://img.shields.io/badge/Conventional%20Commits-1.0.0-%23FE5196?logo=conventionalcommits" /></a>
<a href="https://discord.gg/4WVNHgQ7bP"><img alt="Discord" src="https://img.shields.io/discord/1022471509173882950"></a>
</p>

This project aims to provide a scoped indexer for retrieving ADA Handle information from the Cardano blockchain and exposing it via REST using [Yaci Store](https://github.com/bloxbean/yaci-store).

Design decisions, including the ADA Handle resolution rules we follow (ADA Handles have no CIP), are recorded as
[Architecture Decision Records](docs/adr/README.md).

## Why ADA Handle resolver?
One may ask why an ADA Handle resolver if there is already Kora Labs' [Handles API](https://github.com/koralabs/api.handle.me).

The ADA Handle resolver by the Cardano Foundation differentiates itself with the following traits:
- it demonstrates the use of the Yaci Store modular indexer
- it is written in Java, so it is easy to extend and to reason about for Java developers
- it doesn't depend on other components, e.g. Ogmios, Blockfrost, etc.
- its database has a very small on-disk footprint
- it supports embedded databases such as H2 for easier deployment

## 🔎 What is indexed

Handles are the tokens of ADA Handle's minting policies, the same on mainnet, preprod and preview: the
original policy `f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a` and the DeMi (decentralized
minting) policy `6c32db33a422e0bc2cb535bb850b5a6e9a9572222056d6ddc9cbc26e`. A handle resolves to:

| Handle | Token | Resolves to |
|---|---|---|
| Legacy handle (`$alice`) | asset name = handle name | the address holding the token |
| CIP-68 handle | label 222 user token (`000de140` + name) | the address holding the token |
| NFT sub-handle (`$sub@alice`) | label 222, name `sub@root` | the address holding the token |
| Virtual sub-handle (`$sub@alice`) | label 0 (`00000000` + name), held by a Handle script | the address in its datum (`extra.resolved_addresses.ada`) |

CIP-68 reference tokens (label 100) and sub-handle settings tokens (label 1) are not handles. Labels follow
[CIP-67](https://cips.cardano.org/cip/CIP-0067); the rules match Kora Labs' Handles API, which the
regression suite compares against. [ADA Handle tokens and datums](docs/handle-datums.md) walks through each token and
its datum, field by field, with real mainnet examples.

Burning a handle's token removes the handle, for every kind above (as on Kora Labs' API): it no longer
resolves (404) and leaves the reverse lookups. If the same handle is minted again later, it resolves to
its new holder. Burning a reference or settings token removes nothing.

Chain rollbacks are handled: every ownership change and burn is kept in a history table, and a rollback
restores the affected handles to their state before the abandoned blocks, including a handle whose burn
is rolled back.

## 🚀 Getting Started

### 🐳 Docker

```zsh
docker run -p 9095:9095 -e JAVA_TOOL_OPTIONS=-Xmx4g cardanofoundation/adahandle-resolver:1.0.0-pre1
```

Pin a version like this: `latest` follows final releases only, so it doesn't point at 1.0.0 until 1.0.0 is
released (see Releases below). This syncs mainnet into an embedded H2 database. A mainnet sync needs about 4 GB of heap; pass JVM flags
in `JAVA_TOOL_OPTIONS` (`JAVA_OPTS` is ignored by the image).

### 🐘 Docker Compose with PostgreSQL

```zsh
cp .env.example .env   # mainnet, PostgreSQL, -Xmx4g; change DB_PASSWORD
docker compose up -d
```

The first sync starts at the first ADA Handle mint (slot 47,931,310) and takes several hours on mainnet.

> **Upgrading to 1.0.0 requires a full resync** from an empty database (Yaci Store 2.x changed its
> schema, and 1.0.0 indexes handles it didn't before). Stop the resolver, drop the database
> (`docker compose down -v`, or delete `./data/db*` for H2), then start 1.0.0.

### 🪶 Maven 

#### Prerequisites

Java 25

#### Build & Run

```
git clone https://github.com/cardano-foundation/cf-adahandle-resolver.git
cd cf-adahandle-resolver
./mvnw clean package
java -Xmx4g -jar target/cf-adahandle-resolver-*.jar
```

### ⚡ Native image (experimental)

A GraalVM native image starts in about a second and needs far less memory than the JVM image. Each release
is published as `cardanofoundation/adahandle-resolver:<version>-native`, next to the JVM image `<version>`:

```zsh
docker run -p 9095:9095 cardanofoundation/adahandle-resolver:1.0.0-pre1-native
# build it yourself
docker build -f Dockerfile.native -t adahandle-resolver:native .
```

Configure it with the same environment variables as the JVM image. JVM flags such as `-Xmx` go on the
command line (`docker run ... adahandle-resolver:1.0.0-pre1-native -Xmx2g`), not in `JAVA_TOOL_OPTIONS`.

The native image is also built and smoke-tested every week, without publishing anything, so a change
that breaks it under GraalVM shows up before the next release.

**Memory and metrics:** on mainnet the native image used about 1.1 GB during a full sync and about 250 MB at the
tip (container memory). The JVM's per-pool memory metrics don't exist in it: GraalVM's native image with G1 has
no eden, survivor or old-gen pools, and no GC pause events. `jvm_memory_used_bytes`, `jvm_memory_committed_bytes`
and `jvm_memory_max_bytes` report the heap as a whole instead, under `area="heap", id="heap"`, and
`process_memory_rss_bytes` the resident memory of the process. Its RSS also counts the executable's mapped
pages, which the kernel can reclaim, so it reads higher than the container's memory usage (about 480 MB versus
250 MB at the tip).

**Licensing:** the native image is built with Oracle GraalVM, licensed under the
[GraalVM Free Terms and Conditions](https://www.graalvm.org/faq/) (GFTC). The GraalVM parts compiled into
the executable are covered by the GFTC, which allows free use, including production and hosted services,
and free redistribution, but not redistribution for a fee. The image includes Oracle's license and notices
in `/licenses/graalvm`. The resolver's own code is MIT licensed, and the JVM image doesn't contain GraalVM.

## 🏷️ Releases

Releases follow [semantic versioning](https://semver.org). A release can be preceded by pre-releases, `1.0.0-pre1`,
`1.0.0-pre2`, …, then the final `1.0.0`, following the Cardano Foundation's internal release guidelines
(`cf-java-nfrs`, NFR-08). Each is a GitHub release, the pre-releases marked as such, and each is published to
Docker Hub:

| Version      | JVM image                  | Native image          |
|--------------|----------------------------|-----------------------|
| `1.0.0-pre1` | `:1.0.0-pre1`              | `:1.0.0-pre1-native`  |
| `1.0.0`      | `:1.0.0` and `:latest`     | `:1.0.0-native`       |

`latest` always points at the newest final release, never at a pre-release.

To cut a pre-release or a final, merge into `main` a commit whose message ends with a `Release-As:` footer, e.g.
`Release-As: 1.0.0-pre1`, then merge the release PR that release-please opens.

## 🧪 Tests

- **Unit tests** (`./mvnw test`): handle decoding, rollbacks, the API. 📊 [Coverage Report](https://cardano-foundation.github.io/cf-adahandle-resolver/coverage-report/)
- **Integration tests** (`.github/workflows/integration-tests.yaml`, on every push and pull request): the
  resolver syncs a local [Yaci DevKit](https://github.com/bloxbean/yaci-devkit) devnet into PostgreSQL; the
  tests mint legacy, CIP-68, NFT and virtual sub-handles under two policies, transfer them, restart the
  resolver and check the API. To run them locally (Linux x64, or macOS with Rosetta):
  ```zsh
  yaci-devkit up --enable-yaci-store          # npm install -g @bloxbean/yaci-devkit
  ADAHANDLE_POLICY_IDS=3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712,db63f51afa43f19d42687acb2671c1bdfe04ab8dceb9526e8ceb6935 \
    SPRING_ACTIVE_PROFILES=devnet,postgres DB_URL=jdbc:postgresql://localhost:5432/adahandle \
    DB_USERNAME=adahandle DB_PASSWORD=adahandle java -jar target/cf-adahandle-resolver-*.jar &
  ./mvnw verify -DskipUTs=true -DskipITs=false
  ```
- **Mainnet regression** (Claude Code skills in `.claude/skills`): a full mainnet sync on a remote host,
  then checks against Kora Labs' Handles API and the chain.


## 🤖 API Endpoints

| Endpoint                                                                    | Description                                                                                  |
|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| http://localhost:9095/swagger-ui.html                                       | **Swagger UI** for the API endpoints                                                         |
| http://localhost:9095/apidocs                                               | OpenAPI specification                                                                        |
| http://localhost:9095/api/v1/addresses/by-ada-handle/{adaHandle}            | [GET]s the payment and stake address a handle resolves to (with or without the leading `$`) |
| http://localhost:9095/api/v1/ada-handles/by-stake-address/{stakeAddress}     | [GET]s the handles held by a stake address                                                   |
| http://localhost:9095/api/v1/ada-handles/by-payment-address/{paymentAddress} | [GET]s the handles held by a payment address                                                 |

Every API response says how current its data is, so a client can refuse to send funds to an address resolved from
out-of-date data:

| Header              | Value                                                                                              |
|---------------------|----------------------------------------------------------------------------------------------------|
| `X-Sync-Status`     | `synced` (at the tip, receiving blocks), `syncing` (catching up), `stale` (no block from the node for 300 s), `unknown` |
| `X-Sync-Slot`       | Slot of the last block indexed                                                                     |
| `X-Sync-Block`      | Number of the last block indexed                                                                   |
| `X-Sync-Block-Time` | Time of the last block indexed, ISO 8601 UTC (e.g. `2026-11-06T10:00:00Z`)                         |
| `X-Sync-Tip-Slot`   | Slot of the network tip, as last fetched from the node                                             |

The status is refreshed every 5 seconds in the background ([ADR 12](docs/adr/0012-sync-status-headers.md) explains
why). A `404` from an instance that is `syncing` may only mean the handle isn't indexed yet.

## 📈 Monitoring

| Endpoint                     | What it tells you                                                                            |
|------------------------------|----------------------------------------------------------------------------------------------|
| `/actuator/health/liveness`  | `200` while blocks arrive from the node (Yaci Store's `nodeHealth`)                          |
| `/actuator/health/readiness` | `200` once the sync has reached the tip (`nodeSync`) and the database is up; `503` before    |
| `/actuator/health/startup`   | `200` once the database is up and the node connected                                         |
| `/actuator/health`           | All checks, with details                                                                     |
| `/actuator/prometheus`       | Prometheus metrics, labelled `application="cf-adahandle-resolver"`                           |
| `/api/v1/sync/status`        | Yaci Store's sync status: block reached, tip, `lag_blocks`, `synced`                         |

The Helm chart's probes and the Docker Compose healthcheck use these groups. A fresh instance is not ready
until it has synced, about 6.5 hours on mainnet. The node checks fail after 300 s without a block
(`STORE_BLOCK_RECEIVE_DELAY_SECONDS`).

Metrics about handles, next to Yaci Store's `yaci_store_*` sync metrics and the standard Spring Boot ones:

| Metric                                | Meaning                                                                       |
|---------------------------------------|-------------------------------------------------------------------------------|
| `adahandle_handles{type}`             | Handles that exist now; `type` is `handle` or `subhandle`                    |
| `adahandle_mints_total{type}`         | Handle tokens minted (a legacy handle moved to CIP-68 counts as a mint and a burn) |
| `adahandle_burns_total{type}`         | Handle tokens burned                                                          |
| `adahandle_history_rows_total`        | Handle states written to the history                                          |
| `adahandle_rollbacks_total{outcome}`  | Rollbacks, `noop` or `applied`                                                |
| `adahandle_rollback_handles_total`    | Handles that rollbacks restored or removed                                    |

Memory: `jvm_memory_*_bytes{area="heap"}` (per pool on the JVM, the heap as a whole in the native image) and
`process_memory_rss_bytes`, the resident memory of the process (Linux).

## 👻 Disable Indexer

If you intend to scale this service, running multiple instances of the API is advisable. 
However, you may not want a crawl job on every instance. To disable the indexer (yaci-store), 
utilize the `disable-indexer` profile as an environment variable.

```zsh
SPRING_ACTIVE_PROFILES=mainnet,postgres,disable-indexer java -jar target/cf-adahandle-resolver-*.jar
```

## 🌱 Environment Variables

Possible profiles: `mainnet`, `preprod`, `preview`, `devnet` (a local Yaci DevKit), `local-node`, `h2`, `postgres`, `disable-indexer`

| Name                   | Description                                                    | Default Value                      |
|------------------------|----------------------------------------------------------------|------------------------------------|
| SPRING_ACTIVE_PROFILES | The active profile of the application                          | mainnet,h2                         |
| PORT                   | The port on which the server will listen for incoming requests | 9095                               |
| DB_URL                 | The URL of the database                                        | jdbc:h2:./data/db                  |
| DB_USERNAME            | The username of the database user                              | sa                                 |
| DB_PASSWORD            | The password of the database user                              | password                           |
| REMOTE_NODE_URL        | The URL of the remote node                                     | backbone.cardano.iog.io            |
| REMOTE_NODE_PORT       | The port of the remote node                                    | 3001                               |
| LOCAL_NODE_SOCKET_PATH | The path of the local node socket                              | `<No default value>`               |
| LOCAL_NODE_HOST        | The host of the local node                                     | `<No default value>`               |
| LOCAL_NODE_PORT        | The port of the local node                                     | `<No default value>`               |
| YACI_STORE_CARDANO_SYNC_START_SLOT       | Slot to start syncing from (mainnet: the first ADA Handle mint) | 47931310                 |
| YACI_STORE_CARDANO_SYNC_START_BLOCK_HASH | Hash of the block at that slot                                  | `89f934…a877b`           |
| ADAHANDLE_POLICY_IDS   | Comma-separated minting policies indexed as handles; only change it for tests | both ADA Handle policies |
| STORE_BLOCK_RECEIVE_DELAY_SECONDS | Seconds without a block before the health checks report the node as down | 300 |
| JAVA_TOOL_OPTIONS      | JVM options, e.g. the heap (`-Xmx4g` for a mainnet sync)       | `<No default value>`               |
