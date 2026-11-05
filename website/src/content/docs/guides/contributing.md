---
title: Contributing
description: Build the resolver from source, run its tests, and find the design decisions and handle formats it is built on.
---

The resolver is open source under MIT. This page is for changing it; to run it, start with the
[Quickstart](../../getting-started/quickstart/). Everything below lives in the
[GitHub repository](https://github.com/cardano-foundation/cf-adahandle-resolver).

## Build from source

You need JDK 25. The Maven wrapper is included:

```sh
git clone https://github.com/cardano-foundation/cf-adahandle-resolver.git
cd cf-adahandle-resolver
./mvnw clean package
java -Xmx4g -jar target/cf-adahandle-resolver-*.jar
```

The GraalVM native image is built with Docker, see [Deployment](../deployment/#native-image-experimental):

```sh
docker build -f Dockerfile.native -t adahandle-resolver:native .
```

## Tests

| Level | What it checks | How to run it |
| ----- | -------------- | ------------- |
| Unit | Handle decoding, burns, rollbacks, the API, health and metrics, on H2 | `./mvnw test` |
| Integration | The resolver syncs a local [Yaci DevKit](https://github.com/bloxbean/yaci-devkit) devnet into PostgreSQL; the tests mint, transfer and burn every kind of handle, restart the resolver and check the API | On every push and pull request; locally as below |
| Mainnet regression | A full mainnet sync, then checks against Kora Labs' API and the chain | Claude Code skills in `.claude/skills/`, before releases |

To run the integration tests locally (Linux x64, or macOS with Rosetta), start a devnet and the resolver, then the
tests:

```sh
npm install -g @bloxbean/yaci-devkit
yaci-devkit up --enable-yaci-store
ADAHANDLE_POLICY_IDS=3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712,db63f51afa43f19d42687acb2671c1bdfe04ab8dceb9526e8ceb6935 \
  SPRING_ACTIVE_PROFILES=devnet,postgres DB_URL=jdbc:postgresql://localhost:5432/adahandle \
  DB_USERNAME=adahandle DB_PASSWORD=adahandle java -jar target/cf-adahandle-resolver-*.jar &
./mvnw verify -DskipUTs=true -DskipITs=false
```

The two policy IDs are the test policies the integration tests mint handles with.

## Design decisions

The rules the resolver follows, and why, are recorded as architecture decision records (ADRs) in
[`docs/adr`](https://github.com/cardano-foundation/cf-adahandle-resolver/tree/main/docs/adr).
[ADR 2](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/adr/0002-ada-handle-resolution-rules.md),
the resolution rules taken from Kora Labs' implementation, is the closest thing to a specification. Read the ADRs
before changing how handles are indexed. A change to one of those decisions comes with a new ADR.

Guidelines shared by the Cardano Foundation's Java services, such as native image builds, testing, observability and
releases, are kept in the Cardano Foundation's internal `cf-java-nfrs` repository and are not repeated in the ADRs.

## ADA Handle tokens and datums

[ADA Handle tokens and datums](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/handle-datums.md)
describes every kind of handle: its tokens, its CIP-25 metadata or CIP-68 datum, decoded field by field from real
mainnet data, and how a virtual sub-handle's datum becomes the address the API returns.
[How indexing works](../../reference/architecture/) gives the shorter version.

## Pull requests and releases

- Discuss a change in an [issue](https://github.com/cardano-foundation/cf-adahandle-resolver/issues) first, then open a
  pull request against `develop`. See
  [CONTRIBUTING.md](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/CONTRIBUTING.md).
- Commit messages and pull request titles follow
  [Conventional Commits](https://www.conventionalcommits.org/): release-please builds the changelog from them.
- `develop` is promoted to `main` for a release. A pre-release (`X.Y.Z-preN`) or a final release (`X.Y.Z`) gets its
  version from a `Release-As:` footer on a commit, e.g. `Release-As: 1.1.0`; release-please then opens the release
  pull request. Merging it tags the release and publishes the Docker images.

## This website

The site is built with [Astro](https://astro.build) and [Starlight](https://starlight.astro.build), in `website/`:

```sh
cd website
npm install
npm run dev              # http://localhost:4321/cf-adahandle-resolver/
npm run openapi:fetch -- http://localhost:9095 1.0.0   # refresh the API reference from a running resolver
```

It is published to GitHub Pages from `main`.
