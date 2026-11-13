---
title: Introduction
description: What the ADA Handle Resolver is, what it does and when to use it.
---

[ADA Handles](https://handle.me) are human-readable names such as `$alice`
that point to a Cardano address. Each handle is a native asset of one of the two ADA Handle minting
policies. Whoever holds the token owns the handle, and the handle resolves to the address that holds
it. Virtual sub-handles are the exception: they resolve to an address written in their datum.

The **ADA Handle Resolver** is a _scoped indexer_ from the Cardano Foundation. It follows the chain,
keeps only the transaction outputs and burns of ADA Handle tokens, and serves the current owner of
every handle through a small REST API.

## What it indexes

| Kind                                  | Example        |
| ------------------------------------- | -------------- |
| Legacy handles (CIP-25)               | `$alice`       |
| CIP-68 handles                        | `$alice`       |
| NFT sub-handles                       | `$sub@alice`   |
| Virtual sub-handles                   | `$331@hal`     |

It indexes both ADA Handle minting policies: the original one and DeMi's (decentralized minting).
A burned handle no longer resolves. The rules follow Kora Labs' implementation, the reference for
handle.me and most wallets, and are explained in [How indexing works](../../reference/architecture/). The tokens
and datums behind each kind are decoded field by field in
[ADA Handle tokens and datums](https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/docs/handle-datums.md).

## Use it, or run it

- **Use the public instance.** The Cardano Foundation hosts a resolver for mainnet at
  `https://adahandle.mainnet.cardanofoundation.org`, open to anyone without an API key. See
  [Public mainnet instance](../../reference/api/#public-mainnet-instance).
- **Run your own** for preprod or preview, for full control, or to depend on no one else's server. See the
  [Quickstart](../quickstart/).

## What you can ask it

| Question                                     | Endpoint                                                  |
| -------------------------------------------- | --------------------------------------------------------- |
| Which addresses does `$alice` resolve to?    | `GET /api/v1/addresses/by-ada-handle/{handle}`            |
| Which handles does this stake address hold?  | `GET /api/v1/ada-handles/by-stake-address/{stakeAddress}` |
| Which handles sit at this payment address?   | `GET /api/v1/ada-handles/by-payment-address/{address}`    |

The [REST API reference](../../reference/api/) documents each endpoint in full.

## How it differs from api.handle.me

Kora Labs, the team behind ADA Handle, operates [api.handle.me](https://github.com/koralabs/api.handle.me),
which is the most complete source of handle data, including personalization and metadata. This resolver
gives the same answer to _which address does this handle point to_, and solves a narrower problem:

- **Self-contained.** It needs only a Cardano node to sync from. It doesn't depend on Ogmios,
  Blockfrost, db-sync or any hosted API.
- **Small on disk.** It stores just two tables: the current owner of each handle, and the history of
  ownership changes and burns.
- **Embedded or external database.** H2 works out of the box for a single instance, and PostgreSQL is
  available for production.
- **Readable Java.** The project uses Spring Boot and [Yaci Store](https://github.com/bloxbean/yaci-store).
  It is also a working example of how to build your own scoped indexer with Yaci Store's modular starters.

:::note
The resolver answers _who owns this handle right now_. It doesn't serve handle images, personalization
settings or other CIP-68 metadata.
:::

## Supported networks

| Network | Spring profile | Default sync start slot |
| ------- | -------------- | ----------------------- |
| Mainnet | `mainnet`      | `47931310`              |
| Preprod | `preprod`      | `12182404`              |
| Preview | `preview`      | `1468836`               |
| Devnet  | `devnet`       | `0`                     |

Each network starts syncing from the block of its first ADA Handle mint, so there's no need to replay
the whole chain. The `devnet` profile follows a local [Yaci DevKit](https://github.com/bloxbean/yaci-devkit)
devnet from genesis. To sync from your own node instead of a public relay, add the `local-node` profile. See
[Configuration](../../guides/configuration/).
