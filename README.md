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
One may ask why Ada Handle resolve if there is already: https://github.com/koralabs/handles-public-api

Ada Handler resolver by CF differentiates itself with a number of following traits:
- it demostrates usage of Yaci-Store modular indexer
- is written in Java, thus easier extendable and to easier to reason about by Java Developers
- doesn't have any dependencies to other components, e.g. Ogmios, blockfrost, etc
- handles db has very small on disk footprint
- supports embedded DBs such as H2 for easier deployment
 
## 🚀 Getting Started

### 🐳 Docker

```zsh
docker run -p 9095:9095 cardanofoundation/adahandle-resolver:latest
```

### 🪶 Maven 

#### Prerequisites

Java 25

#### Build & Run

```
git clone https://github.com/cardano-foundation/cf-adahandle-resolver.git
cd cf-adahandle-resolver
./mvnw clean package
java -jar target/cf-adahandle-resolver-0.0.1-SNAPSHOT.jar
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

## 🧪 Test Reports

To ensure the stability and reliability of this project, unit tests have been implemented. By clicking on the link below, you can access the detailed test report.

📊 [Coverage Report](https://cardano-foundation.github.io/cf-adahandle-resolver/coverage-report/)


## 🤖 API Endpoints

| Endpoint                                                         | Description                                             |
|------------------------------------------------------------------|---------------------------------------------------------|
| http://localhost:9095/swagger-ui.html                            | **Swagger UI** for the API endpoints                    |
| http://localhost:9095/api/v1/ada-handles/by-stake-address        | [GET]s the ADA Handle held by a given stake address     |
| http://localhost:9095/api/v1/ada-handles/by-payment-address      | [GET]s the ADA Handle held by a given payment address   |
| http://localhost:9095/api/v1/addresses/by-ada-handle/{adaHandle} | [GET]s the stake and payment addresses behind a provided ADA Handle   |
| http://localhost:9095/api/v1/ada-handles/by-payment-address      | [GET]s the payment address behind a provided ADA Handle |

## 👻 Disable Indexer

If you intend to scale this service, running multiple instances of the API is advisable. 
However, you may not want a crawl job on every instance. To disable the indexer (yaci-store), 
utilize the `disable-indexer` profile as an environment variable.

```zsh
SPRING_ACTIVE_PROFILES=disable-indexer java -jar target/cf-adahandle-resolver-0.0.1-SNAPSHOT.jar
```

## 🌱 Environment Variables

Possible profiles: `mainnet`, `preprod`, `preview`, `local-node`, `h2`, `disable-indexer`

| Name                   | Description                                                    | Default Value                      |
|------------------------|----------------------------------------------------------------|------------------------------------|
| SPRING_ACTIVE_PROFILES | The active profile of the application                          | mainnet,h2                         |
| PORT                   | The port on which the server will listen for incoming requests | 9095                               |
| DB_URL                 | The URL of the database                                        | jdbc:h2:mem:mydb                   |
| DB_USERNAME            | The username of the database user                              | sa                                 |
| DB_PASSWORD            | The password of the database user                              | password                           |
| REMOTE_NODE_URL        | The URL of the remote node                                     | relays-new.cardano-mainnet.iohk.io |
| REMOTE_NODE_PORT       | The port of the remote node                                    | 3001                               |
| LOCAL_NODE_SOCKET_PATH | The path of the local node socket                              | `<No default value>`               |
| LOCAL_NODE_HOST        | The host of the local node                                     | `<No default value>`               |
| LOCAL_NODE_PORT        | The port of the local node                                     | `<No default value>`               |
