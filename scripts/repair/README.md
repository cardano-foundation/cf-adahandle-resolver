# Mainnet repair: block 14,053,493

This release upgrades Yaci to **0.3.12** (Yaci Store stays at 0.1.7).

> **Mainnet only: one-time repair required.**
> Yaci 0.3.11 and earlier read mainnet block **14,053,493** as having **0 transactions**. The block has **137**.
> The resolver does not stop syncing, so a mainnet database that synced past this block (slot 200136161,
> 2026-10-11 07:07 UTC) is missing any ADA Handle UTxOs created in it. Upgrading alone does not fix data that is
> already stored.
>
> Preprod and preview are not affected. Databases that have not yet reached this block only need the upgrade.

## 1. Check whether you are affected

The resolver does not store transactions, so check the sync cursor instead:

```bash
docker compose exec db psql -U "$DB_USERNAME" -d "$DB_NAME" -c \
  "SELECT max(slot) FROM cursor_ WHERE id = 1;"
```

If the result is greater than `200136161` and the instance ran a version with Yaci 0.3.11 or earlier while it passed
that slot, run the repair. Otherwise, just upgrade.

## 2. Repair

The repair script moves the sync cursor back to block 14,053,492. On the next start Yaci Store rolls back to that slot
(the resolver's handle history is rolled back with it) and fetches block 14,053,493 again with the fixed parser. The
script is [`repair-mainnet-block-14053493.sql`](repair-mainnet-block-14053493.sql) in this folder, taken unchanged from
the [Yaci Store v2.0.7 release](https://github.com/bloxbean/yaci-store/blob/v2.0.7/scripts/repair/repair-mainnet-block-14053493.sql)
(its header mentions Yaci 0.4.6; the same bug is in Yaci 0.3.11 and earlier). It is PostgreSQL only.

1. **Stop the resolver. Keep the database running.**

   ```bash
   docker compose stop api
   ```

2. **Upgrade** to the release with Yaci 0.3.12. Do not start the resolver yet.

3. **Run the repair script** (from the repository root):

   ```bash
   docker compose exec -T db psql -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
     -f - < scripts/repair/repair-mainnet-block-14053493.sql
   ```

   If you use a non-default schema, add `-c 'SET search_path TO <schema>'` before `-f -`. Expected output:

   ```
   NOTICE:  Cursor moved from slot ... back to 200136161 (block 14053492). Start yaci-store to re-sync.
   ```

   If it fails, nothing is changed. The script is safe to run more than once, and it does nothing if the cursor has
   not passed the block. Because the resolver has no `transaction` table, the script cannot tell whether the block is
   already complete, so a second run moves the cursor back again (harmless, it only re-syncs from that slot).

4. **Start the resolver:**

   ```bash
   docker compose up -d
   ```

   The log shows `Intersect found at slot: 200136161`, followed by a rollback of the data stored after that slot
   before syncing continues.

### Notes

- **Mainnet detection.** The script confirms mainnet via the first Conway block hash in the `era` table. It aborts
  with `Could not confirm this is a mainnet database` on non-mainnet or empty databases. If your mainnet database has
  not recorded that block for some reason, set `v_force_mainnet := true` at the top of the script.
- **Event publisher id.** The script assumes `store.event-publisher-id` is the default `1`.
- **H2.** The script does not run on H2. For an H2 deployment, delete the database files and resync.
- **Restored snapshots.** A database restored from a snapshot taken after 2026-10-11 07:07 UTC needs the same repair.
