# ADA Handle tokens and datums

ADA Handles have no CIP. This document describes, step by step, the tokens behind each kind of handle and the data
attached to them on chain: CIP-25 metadata for legacy handles, and Plutus datums for everything else. It is built from
Kora Labs' code (they created ADA Handle) and from real mainnet datums, decoded here field by field.

It is reference material. The rules the resolver follows are in the ADRs:
- [ADR 2](adr/0002-ada-handle-resolution-rules.md): which tokens are handles;
- [ADR 3](adr/0003-virtual-sub-handles.md): virtual sub-handles resolve from their datum.

Most of what is described here is **not** read by the resolver. It needs only two things: which token an output holds,
and, for virtual sub-handles, one address in the datum. [What the resolver reads](#what-the-resolver-reads) lists
exactly what it uses.

- [1. Tokens and labels](#1-tokens-and-labels)
- [2. Legacy handles: CIP-25 metadata, no datum](#2-legacy-handles-cip-25-metadata-no-datum)
- [3. CIP-68 handles: user token and reference datum](#3-cip-68-handles-user-token-and-reference-datum)
- [4. NFT sub-handles](#4-nft-sub-handles)
- [5. Virtual sub-handles: the datum is the owner](#5-virtual-sub-handles-the-datum-is-the-owner)
- [6. Sub-handle settings (label 1)](#6-sub-handle-settings-label-1)
- [7. DeMi handles](#7-demi-handles)
- [What the resolver reads](#what-the-resolver-reads)
- [Fetching a datum yourself](#fetching-a-datum-yourself)
- [Sources](#sources)

## 1. Tokens and labels

Every handle token belongs to one of two minting policies:

| Policy | |
|---|---|
| `f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a` | the original ADA Handle policy |
| `6c32db33a422e0bc2cb535bb850b5a6e9a9572222056d6ddc9cbc26e` | DeMi, decentralized minting |

The **asset name** says what a token is. [CIP-67](https://cips.cardano.org/cip/CIP-0067) puts a 4-byte label in front
of the name: `0000 | 16-bit label | CRC-8 of the label | 0000`, written in hex as 8 digits. Legacy handles have no
label; their asset name is the plain name.

| Asset name (hex) | Label | Token | Held by | Datum |
|---|---|---|---|---|
| `626967706579` (`bigpey`) | none | legacy handle (CIP-25) | the owner | none; CIP-25 metadata in the mint transaction |
| `000de140` + name | 222 | CIP-68 user token: a handle, or an NFT sub-handle when the name is `sub@root` | the owner | none |
| `000643b0` + name | 100 | CIP-68 reference token of the user token above | a Handle script | **CIP-68 datum** (§3) |
| `00000000` + `sub@root` | 0 | virtual sub-handle | a Handle script | **CIP-68 datum, including the owner's address** (§5) |
| `00001070` + root | 1 | sub-handle settings of a root handle | a Handle script | **settings datum** (§6) |

The remaining labels in Kora's code, 002 and 444, are not handles either.

A token is a handle only with label 222 or 0, or with no label. In that case the handle's name is the asset name
without the label, decoded as UTF-8.

**Example.** `000de140686f736b79` splits as follows:
- `00 0de1 40 0`: the 4-byte prefix, with label `0x00de` = 222 and CRC-8 `0x14`;
- `686f736b79`: the name, `hosky`.

So it is the CIP-68 handle `$hosky`.

## 2. Legacy handles: CIP-25 metadata, no datum

The first handles, minted from December 2021, are plain [CIP-25](https://cips.cardano.org/cip/CIP-0025) NFTs:
- the token sits in the owner's wallet, without a datum;
- the handle resolves to the address of the output holding it;
- descriptive data lives in the **transaction metadata of the mint**, under label `721`, keyed by policy and then by
  asset name.

Example: `$bigpey`, minted in `831c62f8…fa0ad4`, together with other handles:

```json
{
  "721": {
    "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a": {
      "bigpey": {
        "name": "$bigpey",
        "image": "ipfs://QmUha3nHivnGaevZzZdadB9XZAnns8wAmfWBNWRMbcjMUp",
        "description": "The Handle Standard",
        "website": "https://adahandle.com",
        "augmentations": [],
        "core": {
          "og": 0,
          "prefix": "$",
          "version": 0,
          "termsofuse": "https://adahandle.com/tou",
          "handleEncoding": "utf-8"
        }
      }
    }
  }
}
```

| Field | Meaning |
|---|---|
| `name` | Display name, with the `$` prefix |
| `image` | The handle's image (IPFS) |
| `core.og` | 1 for the original "OG" handles, else 0 |
| `core.prefix`, `core.handleEncoding` | Always `$` and `utf-8` |
| `core.version` | Metadata version, 0 |
| `core.termsofuse`, `website`, `description`, `augmentations` | Fixed values |

Metadata is written only once, at the mint, and can't change. Later data, such as personalization or an address other
than the holder's, needs a datum, which is why ADA Handle moved to CIP-68. Many legacy handles were migrated: one
transaction burned the legacy token and minted the label-222 token of the same name (415 such handles on mainnet, e.g.
`kanemiller`).

## 3. CIP-68 handles: user token and reference datum

A [CIP-68](https://cips.cardano.org/cip/CIP-0068) handle is **two tokens with the same name**:

| | Asset name | Held by | Datum |
|---|---|---|---|
| User token | `000de140` + name | the owner's wallet | none |
| Reference token | `000643b0` + name | a Handle script | the handle's data |

The handle resolves to the address holding the **user token**. The reference token's datum describes the handle: its
image, personalization and so on. This is CIP-68's design: the data sits at a script and can change under that
script's rules, while the user token stays in the owner's wallet.

### The datum, step by step

The datum is Plutus data, serialized as CBOR. Its outer shape is the one CIP-68 defines:

```text
Constr 0 [ metadata, version, extra ]
```

- `metadata`: a map of the NFT's descriptive fields (name, image, …);
- `version`: an integer, `1`;
- `extra`: a map of ADA Handle's own fields (personalization, addresses, …).

The keys in both maps are **byte strings** containing UTF-8 text (`name`, `image`, …). Text values are byte strings
too. Numbers are integers, and booleans are integers `0` or `1`.

Here is how the first bytes of a real datum, that of the virtual sub-handle `$331@hal` (§5), read:

```text
d8 79                      tag 121             -> Constr 0 (tags 121..127 are constructors 0..6)
9f                         indefinite list     -> the constructor's fields begin
   af                      map of 15 entries   -> field 1: metadata
      44 6e616d65          bytes(4)  "name"
      48 2433333140 68616c bytes(8)  "$331@hal"
      45 696d616765        bytes(5)  "image"
      58 38 697066733a2f…  bytes(56) "ipfs://zb2rhinKLgiyp958LDVSDrHv13j…"
      …                    (13 more entries)
   01                      int 1               -> field 2: version
   …                       map                 -> field 3: extra
ff                         break               -> end of the fields
```

### `metadata`: the NFT's descriptive fields

Decoded reference datum of `$hosky` (label 100, at `addr1w8vyuk74…zgvr0`):

```text
Constr 0 [
  { name: "$hosky", image: "ipfs://zdj7WaeaysTxpdyp1fGKu7H3SRqXBiAv6N76L3s6SyzAv7uWt",
    mediaType: "image/jpeg", og: 0, og_number: 0, rarity: "common", length: 5,
    characters: "letters", numeric_modifiers: "", version: 1 },
  1,
  { standard_image: "ipfs://zb2rhWtuJRs48pKAz29kmW6Ez6zC83fGjK3txbZYAEBR9cQPV",
    bg_image: "", pfp_image: "",
    portal:   "ipfs://zb2rhniVZMVuzHtAiA7E3bmWTbywdJ6Rom8T7r3uGAb1hZgQk",
    designer: "ipfs://zb2rhhSSnxAwZAxiAzL7DbFQMm1ZGEMSQGGaZZLZ3Td9u29au",
    socials:  "ipfs://zb2rhoqVWjjAo4LqB1VKfr2VD1KJ7Sb5Zk1UMQW9ni8hW4a6L",
    vendor: "", default: 0,
    last_update_address: h'01f1e4b9aa217a7cb84ebaf9052cf32cf97bf77a36324956983c1ae5711b1b9bed…',
    validated_by: h'4da965a049dfd15ed1ee19fba6e2974a0b79fc416dd1796a1f97f5e1',
    image_hash: h'b3b551137596b4c4b5ec455dd6c4a1e868442776c903e9a0b38ac7b978537126',
    standard_image_hash: h'03bef57696c709dc97705d1c77707d7ae30e659480acaf06ce411ac486a90c4c',
    svg_version: "1.15.0", agreed_terms: "", migrate_sig_required: 0, nsfw: 0, trial: 0 }
]
```

| `metadata` field | Type | Meaning |
|---|---|---|
| `name` | text | Display name with the `$` prefix |
| `image` | text | The current image, personalized if the owner personalized it (IPFS) |
| `mediaType` | text | MIME type of `image`, e.g. `image/jpeg` |
| `og`, `og_number` | 0/1, int | Whether it is one of the OG handles, and its number among them |
| `rarity` | text | Rarity tier from the name's length (Kora's `getRarity`): `legendary` (1 character), `ultra_rare` (2), `rare` (3), `common` (4–7), `basic` (8 and more) |
| `length` | int | Number of characters in the name |
| `characters` | text | Comma-separated kinds of character in the name: `letters`, `numbers`, `special` |
| `numeric_modifiers` | text | For names that are numbers: e.g. `negative`, `decimal`; else empty |
| `version` | int | Metadata version, 1 |
| `handle_type` | text | `handle`, `nft_subhandle` or `virtual_subhandle`. Missing from older datums (like `$hosky`'s); Kora derives the type from the label anyway |
| `sub_rarity`, `sub_length`, `sub_characters`, `sub_numeric_modifiers` | | The same, for the part before the `@` of a sub-handle (§4, §5) |

### `extra`: ADA Handle's own fields

| `extra` field | Type | Meaning |
|---|---|---|
| `standard_image` | text | The unpersonalized image (IPFS) |
| `image_hash`, `standard_image_hash` | bytes | SHA-256 of `image` and `standard_image`, so a client can check the files it fetched |
| `bg_image`, `pfp_image` | text | Background and profile-picture images chosen when personalizing (IPFS), or empty |
| `bg_asset`, `pfp_asset` | bytes | The NFTs (policy id + asset name) those images come from, when they come from an NFT |
| `portal`, `designer`, `socials` | text | IPFS links to JSON documents: the handle's portal settings, its design (fonts, QR code, colours), its social links |
| `vendor` | text | Vendor settings, usually empty |
| `default` | 0/1 | Whether this is the owner's default handle, the one wallets show for their address |
| `resolved_addresses` | map | Addresses the handle resolves to, by chain: `ada` as raw address bytes, other chains as text. **Used only by virtual sub-handles** (§5); for a CIP-68 handle the holder of the user token decides |
| `virtual` | map | Only on virtual sub-handles (§5) |
| `last_update_address` | bytes | Raw address of whoever last changed the data |
| `validated_by` | bytes | Public key hash that validated the change (Kora's) |
| `last_edited_time` | int | Time of the last change, in milliseconds |
| `svg_version` | text | Version of the renderer that drew the image |
| `agreed_terms` | text | URL of the terms of use the owner agreed to |
| `migrate_sig_required`, `trial`, `nsfw` | 0/1 | Flags: whether a migration needs a signature (Kora doesn't document it further); trial handle; not-safe-for-work image |
| `pz_enabled` | 0/1 | Whether personalization is enabled |
| `original_address` | bytes | For sub-handles: the address that first held it |
| `id_hash` | bytes | Identity verification hash, if any |

Fields are optional; datums written at different times carry different sets. Kora reads them by name and ignores
unknown ones.

## 4. NFT sub-handles

An NFT sub-handle such as `$-4@legendary` works exactly like a CIP-68 handle (§3):
- a user token `000de140` + `-4@legendary` sits in the owner's wallet;
- a reference token `000643b0` + `-4@legendary` sits at a Handle script.

The handle resolves to the holder of the user token. What tells it apart from a normal handle is the `@` in its name,
and in the datum:
- `metadata.handle_type` is `nft_subhandle`;
- the `sub_*` fields describe the part before the `@`.

```text
metadata: { name: "$-4@legendary", rarity: "basic", length: 12, characters: "letters",
            handle_type: "nft_subhandle",
            sub_rarity: "ultra_rare", sub_length: 2, sub_characters: "numbers,special",
            sub_numeric_modifiers: "negative", … }
```

The sub-handle's own rarity (`sub_*`, of `-4`) differs from the full name's (`basic`, 12 characters).

## 5. Virtual sub-handles: the datum is the owner

A virtual sub-handle, such as `$331@hal`, is a **single token**: `00000000` + `331@hal`, label 0. It always sits at a
Handle script, so its holder says nothing about who it belongs to. Its datum has the same CIP-68 shape as a reference
datum (§3), and **the owner is the address in `extra.resolved_addresses.ada`**. A virtual sub-handle changes owner when this field
is updated; the token itself stays at the script.

Decoded datum of `$331@hal` (token at `addr1wxktka03…gg6j4ux`):

```text
Constr 0 [
  { name: "$331@hal", rarity: "common", length: 7, characters: "letters,numbers",
    handle_type: "virtual_subhandle", version: 1,
    sub_rarity: "rare", sub_length: 3, sub_characters: "numbers", sub_numeric_modifiers: "", … },
  1,
  { virtual: { expires_time: 1790101781880, public_mint: 0 },
    resolved_addresses: { ada: h'01aa8d2b8f7a752cf94a769ecb8664e9ca767ddc23923072a17a5060976763b8be698165e20997434941f550e62cb008f70c6a785970486f8f' },
    standard_image: "ipfs://zb2rhinKLgiyp958LDVSDrHv13jKyZp8g78FM9sfmMF2bPinF", pz_enabled: 1, … }
]
```

| Field | Meaning |
|---|---|
| `resolved_addresses.ada` | **The owner**: a Cardano address as raw bytes (header byte + key or script hashes), not bech32 text |
| `virtual.expires_time` | When the sub-handle expires, in milliseconds since the epoch (here 2026-09-22). Kora displays it but keeps resolving the sub-handle after it, and so does the resolver |
| `virtual.public_mint` | 0/1: whether it was minted through the root's public minting (see `public_minting_enabled`, §6) |

### From the bytes to the address

`resolved_addresses.ada` holds the 57 raw bytes of a base address. To turn them into what the API returns:

1. **Header byte** `0x01`:
   - the high 4 bits, `0`, give the address type: a base address whose payment and stake parts are both key hashes
     (types 0–3 are base addresses, with key or script parts; 6–7 enterprise addresses);
   - the low 4 bits, `1`, give the network: 1 is mainnet, 0 a testnet.
2. **Payment address:** encode all 57 bytes as bech32 with the prefix `addr` (`addr_test` on a testnet):
   `addr1qx4g62u00f6je722w60vhpnya898vlwuywfrqu4p0fgxp9m8vwutu6vpvh3qn96rf9ql258x9jcq3acvdfu9juzgd78suuhmc6`.
3. **Stake address, for base addresses only:**
   - bytes 29–56 are the stake credential;
   - put a header in front: `0xe0` | network for a key stake part, `0xf0` | network for a script;
   - encode as bech32 with the prefix `stake`: `stake1u9nk8w97dxqktcsfjap5js042rnzevqg7uxx57zewpyxlrcp2dj78`.

   An enterprise address has no stake part, so there is no stake address.

Both addresses equal what Kora's API and the resolver return for `$331@hal`.

The resolver encodes the address for the network it indexes, whatever network the header claims, as Kora does. It
derives a stake address only when the header's network is the indexed one. A datum that isn't readable, or that has no
`resolved_addresses.ada`, leaves the sub-handle unresolved; it is never resolved to the script holding the token.

**Datum hash instead of inline datum.** An output can carry only the hash of its datum, with the datum itself in the
transaction's witnesses. Kora reads it from there. The resolver reads inline datums only. No virtual sub-handle on
mainnet uses a datum hash today.

## 6. Sub-handle settings (label 1)

A root handle's owner configures its sub-handles with a settings token, `00001070` + root name (label 1), at a Handle
script. It is **not a handle**: it never resolves, and burning it removes nothing. Its datum is not CIP-68 shaped. It is
a plain list of 8 elements:

```text
[ nft_settings, virtual_settings, buy_down_price, buy_down_paid, buy_down_percent,
  agreed_terms, migrate_sig_required, payment_address ]
```

Each of the first two elements is a list of 5 elements:

```text
[ public_minting_enabled, pz_enabled, tier_pricing, default_styles, save_original_address ]
```

Decoded datum of `hal`'s settings token:

```text
[ [0, 1, [[1, 5000000]], {}, 0],         nft:     no public minting, personalization on, 5 ADA, no default styles
  [0, 1, [[1, 2000000]], {}, 0],         virtual: no public minting, personalization on, 2 ADA, no default styles
  0, 0, 0,                               buy-down price, paid, percent
  "https://handle.me/$/tou",             agreed_terms
  0,                                     migrate_sig_required
  h'013289d8fe81b841bc0b8db96caac944…' ]  payment_address (raw bytes)
```

| Element | Meaning |
|---|---|
| `public_minting_enabled` | 0/1: anyone may mint sub-handles of this kind under the root |
| `pz_enabled` | 0/1: sub-handles of this kind may be personalized |
| `tier_pricing` | `[[min_length, lovelace], …]`: the mint price by length of the sub-handle's name |
| `default_styles` | Map of default design fields (`bg_image`, `pfp_image`, `font`, QR code settings, …) for new sub-handles |
| `save_original_address` | 0/1: record the first holder in `original_address` |
| `buy_down_price`, `buy_down_paid`, `buy_down_percent` | Kora's "buy down": how much the root's owner has paid to buy down, and the price and percentage that buys (Kora's own comments; not documented further) |
| `agreed_terms` | URL of the terms of use agreed to |
| `migrate_sig_required` | 0/1, as in §3 |
| `payment_address` | Raw address bytes where sub-handle mint payments go |

The resolver used to store these tokens as handles. Since #65 it skips them.

## 7. DeMi handles

Handles minted under the DeMi policy (`6c32db33…`) use the same tokens and datums as the original policy, for example
`$demiking`:
- user token `000de140` + `demiking` in the owner's wallet;
- reference token `000643b0` + `demiking` at a Handle script;
- reference datum `Constr 0 [ { name: "$demiking", handle_type: "handle", … }, 1, { … } ]`.

## What the resolver reads

| Token | What the resolver reads | Ignores |
|---|---|---|
| Legacy (no label) | asset name; address of the output holding it | the CIP-25 metadata |
| User token (222) | asset name; address of the output holding it | — (it has no datum) |
| Virtual sub-handle (0) | asset name; `extra.resolved_addresses.ada` of the inline datum | every other datum field |
| Reference (100), settings (1), others | nothing: these are not handles | everything |
| Burn of a legacy, 222 or 0 token | the handle's name and the burned token | — |

Everything else in these datums is display and personalization data, which handle.me serves and the resolver doesn't.
Code: `mapper/AdaHandleMapper.java` (labels, names, burns) and `mapper/VirtualSubHandleDatum.java` (the datum address).

## Fetching a datum yourself

Koios returns a token's current UTxO with its inline datum, as CBOR (`bytes`) and decoded (`value`). The asset name is
the label plus the name in hex:

```sh
NAME_HEX=$(printf %s '331@hal' | xxd -p)
curl -s -X POST https://api.koios.rest/api/v1/asset_utxos \
  -H 'content-type: application/json' \
  -d "{\"_asset_list\":[[\"f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a\",\"00000000$NAME_HEX\"]],\"_extended\":true}" \
  | jq '.[0].inline_datum'
```

Use `000643b0` for a CIP-68 handle's reference datum, `00000000` for a virtual sub-handle, and `00001070` for a root's
sub-handle settings. A legacy handle's CIP-25 metadata is in `minting_tx_metadata` of `POST /asset_info`.

## Sources

Read on 2026-11-04. The datums are mainnet data as of that day; personalization changes them over time.

- Kora Labs, [kora-labs-common](https://github.com/koralabs/kora-labs-common) `e0ed9f1`:
  - `src/utils/cbor/schema/handleData.ts`: the CIP-68 datum schema;
  - `src/utils/cbor/schema/subHandleSettings.ts`: the settings datum;
  - `src/handles/interfaces/index.ts`: `IHandleMetadata`, `IPzDatum`, `ISubHandleSettings`.
- Kora Labs, [api.handle.me](https://github.com/koralabs/api.handle.me) `3047564`:
  `repositories/handlesRepository.ts`, in `processScannedHandleInfo` (how each label is read),
  `buildPersonalizationData` and `parseSubHandleSettingsDatum`.
- [CIP-25](https://cips.cardano.org/cip/CIP-0025), [CIP-67](https://cips.cardano.org/cip/CIP-0067),
  [CIP-68](https://cips.cardano.org/cip/CIP-0068); CBOR tags for Plutus constructors: [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949)
  and the Plutus data encoding (tags 121–127 for constructors 0–6).
- Mainnet datums fetched from [Koios](https://api.koios.rest): `$hosky`, `$-4@legendary`, `$331@hal`, `hal`'s
  settings and `$demiking`; `$bigpey`'s CIP-25 metadata.
