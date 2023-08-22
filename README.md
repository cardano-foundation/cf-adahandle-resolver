# ADA Handle Resolver 🚧️ Under Construction 🚧️

<p align="left">
<img alt="Tests" src="https://github.com/cardano-foundation/adahandle-resolver/actions/workflows/tests.yaml/badge.svg" />
<img alt="Coverage" src="https://github.com/cardano-foundation/adahandle-resolver/blob/gh-pages/badges/jacoco.svg?raw=true" />
<img alt="Release" src="https://github.com/cardano-foundation/adahandle-resolver/actions/workflows/release.yaml/badge.svg?branch=main" />
<a href="https://conventionalcommits.org"><img alt="conventionalcommits" src="https://img.shields.io/badge/Conventional%20Commits-1.0.0-%23FE5196?logo=conventionalcommits" /></a>
<a href="https://discord.gg/4WVNHgQ7bP"><img alt="Discord" src="https://img.shields.io/discord/1022471509173882950"></a>
</p>

This project aims to provide a scoped indexer for retrieving ADA Handle information from the Cardano blockchain and exposing it via REST using [Yaci Store](https://github.com/bloxbean/yaci-store).

## 🧪 Test Reports

To ensure the stability and reliability of this project, unit tests have been implemented. By clicking on the link below, you can access the detailed test report.

📊 [Coverage Report](https://cardano-foundation.github.io/adahandle-resolver/coverage-report/)

## Getting Started

### Prerequisites

Java 17

```
./mvnw clean package
java -jar target/adahandle-resolver-0.0.1-SNAPSHOT.jar
```

## API Endpoints

- http://localhost:9095/ada-handles/by-stake-address : [GET]s the ADA Handle held by a given stake address
- http://localhost:9095/ada-handles/by-payment-address : [GET]s the ADA Handle held by a given payment address
- http://localhost:9095/addresses/by-ada-handle/{adaHandle} : [GET]s the stake address behind a provided ADA Handle
- http://localhost:9095/ada-handles/by-payment-address : [GET]s the payment address behind a provided ADA Handle
**Swagger UI:**  http://localhost:9095/swagger-ui.html
