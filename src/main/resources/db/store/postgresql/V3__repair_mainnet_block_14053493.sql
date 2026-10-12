-- Repair for mainnet block 14053493, based on yaci-store's repair-mainnet-block-14053493.sql
-- (https://github.com/bloxbean/yaci-store/blob/v2.0.7/scripts/repair/repair-mainnet-block-14053493.sql).
--
-- yaci <= 0.3.11 decoded the 137 transactions of mainnet block 14053493 as 0 transactions, so any
-- ADA Handle outputs in that block were never indexed. This migration points the sync cursor back at
-- block 14053492. On the next sync yaci-store rolls back to slot 200136161 (AdaHandleProcessor
-- rolls back the handle history with it) and fetches 14053493 again with yaci 0.3.12.
--
-- Unlike the upstream script it never fails, so preprod, preview and fresh databases start normally.
-- It does nothing when:
--   * the database is not mainnet (first Shelley / first Conway block hash not in the era table)
--   * the cursor has not passed block 14053492
--   * block 14053493 is already stored with all 137 transactions (only checkable when a
--     transaction table exists; this application does not store transactions)

DO $$
DECLARE
    v_publisher_id  int     := 1;          -- store.event-publisher-id (default 1)
    v_slot          bigint  := 200136161;  -- mainnet block 14053492
    v_hash          text    := '0acc389e7ce34b5ed78537d9e96e21ad6cde8422fc143e27439ff77bfef2cdc5';
    v_prev_hash     text    := '9b1c9f89164522045698a608fd52c0c8983b72c23106560f3365348df4ea9b31';
    v_cur_slot      bigint;
    v_txs           bigint;
BEGIN
    -- 1. Mainnet check. Hashes are mainnet's first Shelley block and first Conway block.
    IF NOT EXISTS (SELECT 1 FROM era
                    WHERE block_hash IN ('aa83acbf5904c0edfe4d79b3689d3d00fcfc553cf360fd2229b98d464c28e9de',
                                         '9aa420cf998dbcceec1abaf83ab26294d278d25527e779050ab334c1fadab16c')) THEN
        RAISE NOTICE 'Not a mainnet database (or not synced into Conway yet). Nothing to do.';
        RETURN;
    END IF;

    -- 2. Has the instance passed the affected block?
    SELECT max(slot) INTO v_cur_slot FROM cursor_ WHERE id = v_publisher_id;
    IF v_cur_slot IS NULL OR v_cur_slot <= v_slot THEN
        RAISE NOTICE 'Cursor (slot %) has not passed block 14053492. Nothing to do.', v_cur_slot;
        RETURN;
    END IF;

    -- 3. Already correct?
    IF to_regclass('transaction') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM transaction WHERE block = 14053493' INTO v_txs;
        IF v_txs = 137 THEN
            RAISE NOTICE 'Block 14053493 already has all 137 transactions. Nothing to do.';
            RETURN;
        END IF;
        RAISE NOTICE 'Block 14053493 has % of 137 transactions. Repairing.', v_txs;
    END IF;

    -- 4. Point the cursor at block 14053492.
    DELETE FROM cursor_ WHERE id = v_publisher_id AND slot > v_slot;

    INSERT INTO cursor_ (id, block_hash, slot, block_number, era, prev_block_hash,
                         create_datetime, update_datetime)
    VALUES (v_publisher_id, v_hash, v_slot, 14053492, 7, v_prev_hash,
            now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC')
    ON CONFLICT (id, block_hash) DO UPDATE
        SET slot = EXCLUDED.slot, block_number = EXCLUDED.block_number, era = EXCLUDED.era,
            prev_block_hash = EXCLUDED.prev_block_hash, update_datetime = EXCLUDED.update_datetime;

    RAISE NOTICE 'Cursor moved from slot % back to % (block 14053492). Sync will roll back and re-fetch block 14053493.',
                 v_cur_slot, v_slot;
END $$;
