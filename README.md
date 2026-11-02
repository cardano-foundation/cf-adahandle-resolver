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
regression suite compares against.

Chain rollbacks are handled: every ownership change is kept in a history table, and a rollback restores
the affected handles to their owner before the abandoned blocks. A burned handle keeps resolving to its
last holder.

## 🚀 Getting Started

### 🐳 Docker

```zsh
docker run -p 9095:9095 -e JAVA_TOOL_OPTIONS=-Xmx4g cardanofoundation/adahandle-resolver:latest
```

This syncs mainnet into an embedded H2 database. A mainnet sync needs about 4 GB of heap; pass JVM flags
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
java -Xmx4g -jar target/cf-adahandle-resolver-*-SNAPSHOT.jar
```

### ⚡ Native image (weekly, experimental)

A GraalVM native image starts in about a second and needs far less memory than the JVM image. It is
built weekly from `main` (the released code) and published as `cardanofoundation/adahandle-resolver:native-weekly`:

```zsh
docker run -p 9095:9095 cardanofoundation/adahandle-resolver:native-weekly
# build it yourself
docker build -f Dockerfile.native -t adahandle-resolver:native .
```

Configure it with the same environment variables as the JVM image. JVM flags such as `-Xmx` go on the
command line (`docker run ... adahandle-resolver:native-weekly -Xmx2g`), not in `JAVA_TOOL_OPTIONS`.

**Licensing:** the native image is built with Oracle GraalVM, licensed under the
[GraalVM Free Terms and Conditions](https://www.graalvm.org/faq/) (GFTC). The GraalVM parts compiled into
the executable are covered by the GFTC, which allows free use, including production and hosted services,
and free redistribution, but not redistribution for a fee. The image includes Oracle's license and notices
in `/licenses/graalvm`. The resolver's own code is MIT licensed, and the JVM image doesn't contain GraalVM.

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
    DB_USERNAME=adahandle DB_PASSWORD=adahandle java -jar target/cf-adahandle-resolver-*-SNAPSHOT.jar &
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

## 👻 Disable Indexer

If you intend to scale this service, running multiple instances of the API is advisable. 
However, you may not want a crawl job on every instance. To disable the indexer (yaci-store), 
utilize the `disable-indexer` profile as an environment variable.

```zsh
SPRING_ACTIVE_PROFILES=mainnet,postgres,disable-indexer java -jar target/cf-adahandle-resolver-*-SNAPSHOT.jar
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
| JAVA_TOOL_OPTIONS      | JVM options, e.g. the heap (`-Xmx4g` for a mainnet sync)       | `<No default value>`               |
