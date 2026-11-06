# Agent guidelines

Guidance for AI coding agents (and people) working on this repository. Tool-specific files such as `CLAUDE.md` only
point here.

## What this is

A Spring Boot service that resolves ADA Handles to addresses. It embeds Yaci Store to follow the chain from a Cardano
node, keeps the handles of the ADA Handle minting policies (`adahandle.policy-ids`) in PostgreSQL or H2, and serves
them over a REST API. What counts as a handle, burns, sub-handles and rollbacks are described in the README
("What is indexed") and decided in the ADRs under [`docs/adr/`](docs/adr/). Read the relevant ADR before changing
resolution rules.

Packages under `org.cardanofoundation.tools.adahandle.resolver`:

| Package | Role |
|---|---|
| `storage` | Yaci Store hooks: picks handle tokens out of each block, applies mints, transfers, burns and rollbacks |
| `mapper` | Decodes asset names and datums into handles (legacy, CIP-68, NFT and virtual sub-handles) |
| `entity`, `repository` | JPA entities and Spring Data repositories (current handles and their history) |
| `service`, `controller` | Lookups and the REST API (`/api/v1`) |
| `sync` | Sync status of the indexer, exposed as `X-Sync-*` response headers |
| `metrics` | Custom Micrometer metrics (`adahandle.*`) and memory metrics for the native image |
| `config` | Policies, OpenAPI, web and GraalVM native image hints |

Database migrations: `src/main/resources/db/migration/adahandle/` (Flyway).

## Key commands

```zsh
./mvnw test                                   # unit tests
./mvnw clean package -DskipTests              # build the jar
./mvnw verify -DskipUTs=true -DskipITs=false  # integration tests, need a Yaci DevKit devnet (see README "Tests")
./mvnw -Pnative -DskipTests native:compile    # GraalVM native image
docker compose up                             # resolver + PostgreSQL
```

## Conventions

- Java 25, Spring Boot 3.5. Constructor injection with Lombok `@RequiredArgsConstructor`; `@Autowired` only on the
  constructor Spring should use when a class has more than one.
- Nullness: anything that can be `null` is annotated `@Nullable` from JSpecify (`org.jspecify.annotations`).
  Collections are empty, not `null`.
- Prefer explicit types over `var`.
- Tests: JUnit 5, Mockito, Awaitility for asynchronous behaviour (no `Thread.sleep`). Integration tests are `*IT`
  classes run by failsafe and skipped unless `-DskipITs=false`.
- A new reflective or resource dependency needs a hint in `config/NativeImageConfig.java`; the weekly native build
  (`.github/workflows/native-image.yaml`) catches what is missed.
- Conventional Commits; release-please writes `CHANGELOG.md`. Releases and pre-releases (`X.Y.Z-preN`) are cut as
  described in the README ("Releases").
- Decisions with lasting consequences get an ADR in `docs/adr/`.

## Skills

QA skills live in `.claude/skills/` as plain `SKILL.md` instructions plus shell scripts, so any agent can follow them:

- `cf-adahandle-resolver-regression`: checks a synced instance against Kora Labs' Handles API and the chain.
- `cf-adahandle-resolver-mainnet-integration-test-full-sync`: a full mainnet sync on a remote host.

They read host settings from git-ignored config files and confirm before anything destructive on a remote host.

## Standards

This service follows the Cardano Foundation Java microservice guidelines
([`cardano-foundation/cf-java-nfrs`](https://github.com/cardano-foundation/cf-java-nfrs),
`java-microservice-nfrs/`). Where this file and the guidelines differ, this file describes what the service does
today.
