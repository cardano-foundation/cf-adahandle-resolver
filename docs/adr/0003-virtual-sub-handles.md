# 3. Virtual sub-handles resolve to the address in their datum

- Status: Accepted
- Date: 2026-11-03

## Context

A virtual sub-handle (`sub@root`, CIP-67 label 0) is a token held by a Handle script, not by its owner. Treating it like
other handle tokens resolved it to the script: before #65, 1,104 virtual sub-handles on mainnet didn't resolve to
their owner, and 984 sub-handle settings tokens (label 1) were stored as handles.

The owner is in the token's datum, which follows CIP-68: `Constr 0 [metadata, version, extra]`, with the address at
`extra.resolved_addresses.ada` as raw address bytes. Kora reads it there
(`handlesRepository.ts::processScannedHandleInfo`, `AssetNameLabel.LBL_000`).

## Decision

- A label-0 token resolves to `extra.resolved_addresses.ada` of its inline datum, encoded for the network being indexed,
  as Kora does. A stake address is derived only when the datum's address is a base address of that same network.
- A label-0 token without a readable datum is skipped (logged), never resolved to the script holding it.
- When the datum changes (the token is sent with a new datum), the sub-handle follows it; the history keeps every
  address, so rollbacks work as for other handles.
- Label-1 tokens (sub-handle settings) are not handles.

## Consequences

- Compared with Kora on mainnet: 1,083 of 1,083 virtual sub-handles match (#65), and in the full run of 2026-10-30
  1,082 virtual and 2,697 NFT sub-handles matched, none missing.
- Expiry (`virtual.expires_time`) is ignored, as Kora ignores it when resolving.
- Datum hashes (datum in the witnesses) are not supported yet ([ADR 2](0002-ada-handle-resolution-rules.md)).

## References

- #65; `mapper/VirtualSubHandleDatum.java`, `mapper/AdaHandleMapper.java`.
