# 2. ADA Handle resolution rules, following Kora Labs' implementation

- Status: Accepted
- Date: 2026-11-03

## Context

There is no CIP for ADA Handles. The token standards they use are public:

- [CIP-25](https://cips.cardano.org/cip/CIP-0025): legacy NFTs, where the asset name is the handle;
- [CIP-67](https://cips.cardano.org/cip/CIP-0067): a 4-byte label at the start of an asset name,
  `0000 | 16-bit label | CRC-8 of the label | 0000`;
- [CIP-68](https://cips.cardano.org/cip/CIP-0068): a user token paired with a reference token (label 100) that holds the
  metadata datum.

What they don't say is which tokens are handles, whom a handle resolves to, or what happens on a burn. Those rules are
defined only by the code of Kora Labs, who mint ADA Handles and run [api.handle.me](https://api.handle.me), the API
behind handle.me and most wallets. A resolver that answers differently from it gives users a different address for the
same handle.

## Decision

We follow Kora Labs' implementation, and use their API as the reference our tests compare against. The rules below
were read from their open-source code (we reimplement the behaviour; no code is copied):

- [koralabs/api.handle.me](https://github.com/koralabs/api.handle.me) (MPL-2.0):
  `services/ogmios/ogmios.service.ts::processBlock` (outputs, burns, ordering) and
  `repositories/handlesRepository.ts::processScannedHandleInfo` (labels, datum, slots);
- [koralabs/kora-labs-common](https://github.com/koralabs/kora-labs-common) (GPL-3.0):
  `src/handles/policies.ts` (policies), `src/utils/crypto/index.ts::checkNameLabel` and `src/types` `AssetNameLabel`
  (labels).

Read at api.handle.me `3047564` and kora-labs-common `e0ed9f1` (2026-11-01).

### Handle tokens

A handle is a token of one of the ADA Handle minting policies ([ADR 4](0004-ada-handle-policies.md)) whose asset name
has one of these forms:

| Asset name | Handle kind | The handle resolves to |
|---|---|---|
| No CIP-67 label: the name itself (`alice`) | legacy handle (CIP-25) | the address of the output holding the token |
| Label 222 (`000de140` + name) | CIP-68 handle; an NFT sub-handle when the name is `sub@root` | the address of the output holding the token |
| Label 0 (`00000000` + name, `sub@root`) | virtual sub-handle | the address in the token's datum ([ADR 3](0003-virtual-sub-handles.md)) |

Other labels are not handles and never own an address: 100 (CIP-68 reference token, holds the metadata), 1 (sub-handle
settings of a root handle), 002 and 444. A label is only a label if its CRC-8 checksum is right; legacy names are
printable ASCII and can't start with one.

### Ownership over time

- A later slot wins. Within a block, the last transaction wins.
- Burning the token of a handle removes the handle ([ADR 5](0005-burned-handles.md)).
- A rollback restores what was true at the rollback point ([ADR 6](0006-rollbacks-through-history.md)).

### Addresses

The resolver serves the ADA payment address and, for base addresses, the stake address. A handle held by an enterprise
or script address has no stake address. Handle names are looked up as given, with or without a leading `$`.

### What we don't follow (yet)

- Kora also reads a virtual sub-handle's datum from the transaction's witnesses when the output carries only a datum
  hash; we read inline datums only. No mainnet virtual sub-handle uses a datum hash today (all 1,082 match Kora).
- Kora answers 202 while it is catching up or rolling back, and 451 for legally restricted names; we answer 200 and 404.
- Kora serves addresses on other chains from the reference datum; we serve ADA only.

## Consequences

- When Kora changes a rule, this ADR and the resolver follow, through a new ADR.
- The regression suite compares the resolver with Kora's API: random handles, golden handles of every kind, every
  sub-handle, and coverage of each policy's tokens from the chain ([ADR 9](0009-testing-strategy.md)).
- A mismatch with Kora is a bug on our side unless an ADR says otherwise.
