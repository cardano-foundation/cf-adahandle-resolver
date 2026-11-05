# Changelog

## [1.0.0-pre1](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.1.5...v1.0.0-pre1) (2026-11-05)


### ⚠ BREAKING CHANGES

* yaci-store 2.x changed its UTXO migration V0_200_1 in place, so databases created with 0.1.7 fail Flyway validation. A full resync from an empty database is required.

### Features

* expose OpenAPI spec at /apidocs with Swagger UI ([#60](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/60)) ([edaa986](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/edaa986bf26a6b0bd081773d15d844b9f2c95d4f))
* handle chain rollbacks correctly, including CIP-68 handles ([#54](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/54)) ([9e7dbe4](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/9e7dbe4769a3e5e1cdcc1f03d5f78ffd09d02dd7))
* health probes and metrics on par with cf-token-metadata-registry ([#93](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/93)) ([d75d154](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/d75d154a5444b11254a2e05a66f8220dcfe114f5))
* Prometheus metrics about handles ([#94](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/94)) ([7e38850](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/7e38850c59b253e4ae411b067ff8d6e758b5dd9a))
* resolve virtual sub-handles from their datum ([#65](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/65)) ([195eac4](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/195eac481d02931b8b385a7a8add3e5e39ff2d3e))
* **skills:** golden handles of every kind, checked against Kora; all sub-handles too ([#77](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/77)) ([a79e831](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/a79e83127c9fda664243d3b632beafb621cf36fe))
* **skills:** regression check that every ADA Handle policy's handles are indexed ([#72](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/72)) ([b05f9b4](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/b05f9b490960ffa37abe63aad433cfbdce2b4970))
* **skills:** regression suite for a synced mainnet instance ([#64](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/64)) ([3b130ee](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/3b130eeb85a60050b495571eca7b055e2c4f8b50))
* weekly GraalVM native image build ([#63](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/63)) ([926a472](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/926a4722466f0b932ba7d7a6676285188e1246da))


### Bug Fixes

* **compose:** restart both services after a crash and after a reboot ([#75](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/75)) ([eccb572](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/eccb5728ba327b7b3666e176c33fc8fbd35f3223))
* **deps:** PostgreSQL driver 42.7.14 for known CVEs ([#67](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/67)) ([c51f078](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/c51f078f8f18756bcd2471c48d68f37582136b3e))
* **deps:** security patch releases for Tomcat, Jackson, Netty, Bouncy Castle and more ([#69](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/69)) ([9940b82](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/9940b82bb9fa783c400c4386f57c03ee3373e4c8))
* **docker:** use the Ubuntu noble JRE so the image runs on x86-64-v2 CPUs ([#62](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/62)) ([a59c3a2](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/a59c3a23c535e21085d90f712c9a2f6f0376b72a))
* implement equals/hashCode on the history composite key ([#66](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/66)) ([c3cdd93](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/c3cdd93d5a6331eb8597707db2af3a355ceacc0d))
* index handles of the DeMi policy too ([#71](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/71)) ([8124cf4](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/8124cf41b02e09a5dac44d3188ab8df5a29285e4))
* keep legacy handles migrated to CIP-68 in one transaction ([#86](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/86)) ([fad12aa](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/fad12aade46b139e9c148de463e78f5261ded07a))
* remove handles whose token is burned ([#78](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/78)) ([6d17c9e](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/6d17c9e6d49eb969ed34686096d4b7fd3f1d2f79))


### Documentation

* ADA Handle tokens and datums, field by field ([#87](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/87)) ([ebd72d5](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/ebd72d518f2548bec3e6548ecc2ec40ed20de281))
* ADR 10, keep JPA for the indexer's writes ([#84](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/84)) ([a61a62c](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/a61a62c8065e9d2ca238d8ae31e1b862a0c984a7))
* ADR 11, apply handle changes at Yaci Store's CommitEvent ([#88](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/88)) ([ff82521](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/ff82521cf3a60b0dd1d5cb141da71394e4bc0a0c))
* architecture decision records ([#83](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/83)) ([7c0c5b0](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/7c0c5b000b50420de147ce4f3be09338cdac1a85))
* bring the README up to date for 1.0.0 ([#74](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/74)) ([02d6d50](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/02d6d5096a2b8d8092ac7fc220c2608743631976))
* monitoring in the README: health groups and the handle metrics ([#93](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/93), [#94](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/94)) ([#96](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/96)) ([c8693a8](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/c8693a82a616ad876a511496208bd4ce7e433f8d))


### Miscellaneous Chores

* release 1.0.0 ([dcfe1ae](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/dcfe1aeef2380ead36fde3d6d910048d5f4d77bc))
* release 1.0.0-pre1 ([155511c](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/155511c30123d18dce109dab8658cc35b9aa6de0))
* upgrade yaci-store to 2.0.7 ([#57](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/57)) ([b283f66](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/b283f6607d436a344071060469e7b909d8489ad7))

## [0.1.4](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.1.3...v0.1.4) (2025-05-08)


### Bug Fixes

* Upgrade to Yaci Store 0.1.2, Spring Boot 3.3.11 ([#48](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/48)) ([ef8d828](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/ef8d828102889230481d81b7689898d4ad595ebf))

## [0.1.3](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.1.2...v0.1.3) (2024-01-09)


### Features

* use composit key for ada handle history items ([1393041](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/1393041bf961a1dd122814fb49c66db73166572d))


### Bug Fixes

* detect cip68-222 token and decode the asset name from the unit field. Ignore cip68 100 token. Fixes [#43](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/43) ([9b6c482](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/9b6c482a3017cb6790ec9e3c4fbcd5b50efcfbcf))
* fetch all exceptions in case of a decoding issue ([d876520](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/d8765205e06234821c904f90322dd5ed3e6f873b))
* introduce a test order ([5ddaad9](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/5ddaad931470ab2e01c1131e9718b0ebbbe14ae6))

## [0.1.2](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.1.1...v0.1.2) (2023-12-04)


### Bug Fixes

* add config to prevent cors issues ([7e2b311](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/7e2b3116f4bfd86a0e71c6062231975fa763103c))


### Documentation

* improved docs ([348b183](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/348b1834f1d86646bdbe38d4d94769c89008aedf))
* introduced why ADA Handle Resolver section ([75c2cd5](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/75c2cd514c43a2fe15e326061ae9526fa0b18ca3))

## [0.1.1](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.1.0...v0.1.1) (2023-11-30)


### Features

* added env var in spring config to overwrite block hash slot to sycn from ([#36](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/36)) ([a0929d4](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/a0929d4dab09119e91e4f658717c0948c0a455a7))

## [0.1.0](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.0.6...v0.1.0) (2023-11-28)


### ⚠ BREAKING CHANGES

* add payment address to by-ada-handle endpoint. Fixes #29

### Features

* add payment address to by-ada-handle endpoint. Fixes [#29](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/29) ([69c056e](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/69c056e4f7dc73ac941629136400e4892a56eeea))
* allow to run the application without the crawl job due to a new disable-indexer profile. Closes [#26](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/26) ([a792025](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/a792025a2b505f65d70b77b21ee03751613cd550))


### Bug Fixes

* check case when adahandle would be just a dollar sign ([2645f9a](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/2645f9ad40f2189c4ed7fef29dc4c856fc86cce9))
* ignore dollar sign if present. Solves [#28](https://github.com/cardano-foundation/cf-adahandle-resolver/issues/28) ([745c223](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/745c2231248c4ab66b64ad50f4ce05f4d23d2a34))
* repair test and add edge case test ([235dfdb](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/235dfdba14112222a80d2edf9397bb77f44dc97b))

## [0.0.6](https://github.com/cardano-foundation/adahandle-resolver/compare/v0.0.5...v0.0.6) (2023-08-31)


### Bug Fixes

* make password customizable and use random pw as a fallback ([ce07251](https://github.com/cardano-foundation/adahandle-resolver/commit/ce07251f7e858cc565c4ee4edad4150b13419b68))

## [0.0.5](https://github.com/cardano-foundation/adahandle-resolver/compare/v0.0.4...v0.0.5) (2023-08-23)


### Features

* add docker description in pipeline ([c0e0025](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/c0e00255ca9de8c4fd855ef8e38f3d720ced5a71))

## [0.0.4](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.0.3...v0.0.4) (2023-08-23)


### Features

* add environment variables to make the tool customizable ([4cdf5ac](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/4cdf5acb60f405b102cf6ac3fb5b2787b3eed110))

## [0.0.3](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.0.2...v0.0.3) (2023-08-22)


### Bug Fixes

* **docker:** change short description to have less than 100 bytes as required by DockerHub ([b6516d2](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/b6516d2a621d7a42b01152637ef1ea09a574062d))
* prevent error 'no matching manifest for linux/arm64/v8' by rollback the gh action to v3 and add push for the readme description ([9aeaeca](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/9aeaeca3c8ec477a58c8d5d379a65013fec0571c))

## [0.0.2](https://github.com/cardano-foundation/cf-adahandle-resolver/compare/v0.0.1...v0.0.2) (2023-08-22)


### Features

* add publish to dockerhub pipeline ([aa5916b](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/aa5916b04e6088a440b737302a711036c1cc00ea))

## 0.0.1 (2023-08-22)


### Features

* add pipeline to run tests and create a new release ([42896e0](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/42896e07b709248055fbb169e56a1b49e50014c2))
* add tests to check if the rollback works as expected ([3a5012f](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/3a5012f0724b75b7648faf72a9acf7090bdf617d))
* adding the basic structure for an adahandle resolver using yaci-store ([de28895](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/de28895ee1470310743d44104e341f099480efc1))
* allow to search adahandles by stake addresses ([f5018a5](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/f5018a568400d536bf12f774e7e821773ca33013))
* handle rollbacks by tracking the history in a seperate table ([f681f8c](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/f681f8c96923c7462ca35ded3885844b04d66b34))


### Bug Fixes

* docker build pipeline ([51e6947](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/51e6947dcb73d2599b2b526dfee80651da864b28))
* don't store utxos as we don't need them anymore ([f7fbbe6](https://github.com/cardano-foundation/cf-adahandle-resolver/commit/f7fbbe6014d4f92e91874cc28d4f7f64238178de))
