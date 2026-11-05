# 7. Yaci Store 2.x, and a full resync for 1.0.0

- Status: Accepted
- Date: 2026-11-03

## Context

The resolver is built on [Yaci Store](https://github.com/bloxbean/yaci-store). Its 0.1.x line is retiring, and its yaci
release had a parser bug on mainnet block 14,053,493. Yaci Store 2.x is maintained, but changed its UTXO migration
`V0_200_1` in place, so a database created with 0.x fails Flyway validation (`Migration checksum mismatch`).

## Decision

- Use Yaci Store 2.0.7 (yaci 0.4.7) with Spring Boot 3.5 (Yaci Store 2.x supports Spring Boot 3 only) on Java 25.
- Release it as **1.0.0** with a full resync from an empty database. 1.0.0 also indexes handles it didn't before
  (virtual sub-handles, DeMi) and removes burned ones, which only a resync applies to existing data.
- Index from the first ADA Handle mint (mainnet slot 47,931,310), not from genesis.

## Consequences

- Upgrading operators drop their database and resync: about 7 to 12 hours on mainnet.
- Spring Boot 4 has to wait for a Yaci Store release that supports it.
- Every release is tested with a full mainnet sync and the regression suite ([ADR 9](0009-testing-strategy.md)).

## References

- #57, #61; release notes in #59.
