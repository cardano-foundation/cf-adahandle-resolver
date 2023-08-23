# Changelog

## [0.0.4](https://github.com/cardano-foundation/adahandle-resolver/compare/v0.0.3...v0.0.4) (2023-08-23)


### Features

* add environment variables to make the tool customizable ([4cdf5ac](https://github.com/cardano-foundation/adahandle-resolver/commit/4cdf5acb60f405b102cf6ac3fb5b2787b3eed110))

## [0.0.3](https://github.com/cardano-foundation/adahandle-resolver/compare/v0.0.2...v0.0.3) (2023-08-22)


### Bug Fixes

* **docker:** change short description to have less than 100 bytes as required by DockerHub ([b6516d2](https://github.com/cardano-foundation/adahandle-resolver/commit/b6516d2a621d7a42b01152637ef1ea09a574062d))
* prevent error 'no matching manifest for linux/arm64/v8' by rollback the gh action to v3 and add push for the readme description ([9aeaeca](https://github.com/cardano-foundation/adahandle-resolver/commit/9aeaeca3c8ec477a58c8d5d379a65013fec0571c))

## [0.0.2](https://github.com/cardano-foundation/adahandle-resolver/compare/v0.0.1...v0.0.2) (2023-08-22)


### Features

* add publish to dockerhub pipeline ([aa5916b](https://github.com/cardano-foundation/adahandle-resolver/commit/aa5916b04e6088a440b737302a711036c1cc00ea))

## 0.0.1 (2023-08-22)


### Features

* add pipeline to run tests and create a new release ([42896e0](https://github.com/cardano-foundation/adahandle-resolver/commit/42896e07b709248055fbb169e56a1b49e50014c2))
* add tests to check if the rollback works as expected ([3a5012f](https://github.com/cardano-foundation/adahandle-resolver/commit/3a5012f0724b75b7648faf72a9acf7090bdf617d))
* adding the basic structure for an adahandle resolver using yaci-store ([de28895](https://github.com/cardano-foundation/adahandle-resolver/commit/de28895ee1470310743d44104e341f099480efc1))
* allow to search adahandles by stake addresses ([f5018a5](https://github.com/cardano-foundation/adahandle-resolver/commit/f5018a568400d536bf12f774e7e821773ca33013))
* handle rollbacks by tracking the history in a seperate table ([f681f8c](https://github.com/cardano-foundation/adahandle-resolver/commit/f681f8c96923c7462ca35ded3885844b04d66b34))


### Bug Fixes

* docker build pipeline ([51e6947](https://github.com/cardano-foundation/adahandle-resolver/commit/51e6947dcb73d2599b2b526dfee80651da864b28))
* don't store utxos as we don't need them anymore ([f7fbbe6](https://github.com/cardano-foundation/adahandle-resolver/commit/f7fbbe6014d4f92e91874cc28d4f7f64238178de))
