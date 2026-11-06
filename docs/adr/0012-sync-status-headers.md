# 12. Every API response says how current its data is, in X-Sync-* headers

- Status: Accepted
- Date: 2026-11-06

## Context

A client could not tell how current an answer is (#27). A handle resolved from out-of-date data can point at an address
that no longer holds it, and funds sent there go to the wrong owner. The data is out of date while an instance syncs
(about 6.5 hours on mainnet), and when it loses its node connection.

The readiness probe (#93) keeps such an instance out of a load balancer's rotation, but only where there is one: a
client calling an instance directly, or a deployment without probes, still gets the answer with no warning. A `404`
from an instance still syncing may only mean the handle is not indexed yet.

## Decision

- Every `/api/**` response, `404`s included, carries the sync status in headers:
  - `X-Sync-Status`: `synced`, `syncing`, `stale` or `unknown`;
  - `X-Sync-Slot`, `X-Sync-Block`: the last block indexed;
  - `X-Sync-Block-Time`: that block's time (ISO 8601, UTC), so a client can work out the data's age itself;
  - `X-Sync-Tip-Slot`: the network tip.

  A value that is not known is left out.
- **Headers, not body fields.** The response bodies stay as they are, so existing clients and the API contract are
  unaffected, and the same headers fit every endpoint, whether it returns an object, a list or a `404`. CORS exposes
  them to browser scripts; the OpenAPI spec documents them.
- **The status comes from Yaci Store:** its cursor (last block), cached network tip and block-receive health. `synced`
  means within a few blocks of the tip, as Yaci Store's `nodeSync` judges it. **Not receiving blocks wins over being at
  the tip:** with no block for `store.block-receive-delay-seconds` (300 s), or the block fetcher stopped or in error,
  the status is `stale`, whatever the last known tip said.
- **Read from a snapshot refreshed in the background every 5 seconds, not per request.** Yaci Store's sync status
  refreshes the network tip inside the call, a round trip to the node under a lock, every few minutes and every 30 s
  after a failure. Per request, an API call would then wait for the node now and then, and all concurrent ones behind
  it, longest when the node is slow or unreachable, just when the answer should be `stale`. The snapshot is read from a
  field; requests never touch the node or the database for it.

## Consequences

- A client can refuse an answer that is not `synced`, or one whose block time is too old, before sending funds.
- The headers lag the data by up to 5 seconds, under one block (a block comes every 20 s on average). When they lag,
  they show the data as older than it is, never newer.
- `stale` follows the same 300 s threshold as the liveness probe, so the API and the probes agree on what is stale.
- Monitoring stays where it is (health groups, Prometheus); the headers are for the API's clients.

## References

- #27, #93; `sync/SyncStatusProvider.java`, `sync/SyncStatusHeaders.java`, `config/WebConfig.java`,
  `config/OpenApiConfig.java`.
