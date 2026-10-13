-- H2 version of db/store/postgresql/V3__repair_mainnet_block_14053493.sql (see there for details).
-- Points the sync cursor back at mainnet block 14053492 so block 14053493 is fetched again with
-- yaci 0.3.12. Does nothing unless the database is mainnet and the cursor has passed block 14053492.
-- There is no transaction table in this application, so the 137-transaction check does not apply.

INSERT INTO cursor_ (id, block_hash, slot, block_number, era, prev_block_hash, create_datetime, update_datetime)
SELECT 1, '0acc389e7ce34b5ed78537d9e96e21ad6cde8422fc143e27439ff77bfef2cdc5', 200136161, 14053492, 7,
       '9b1c9f89164522045698a608fd52c0c8983b72c23106560f3365348df4ea9b31', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM era
               WHERE block_hash IN ('aa83acbf5904c0edfe4d79b3689d3d00fcfc553cf360fd2229b98d464c28e9de',
                                    '9aa420cf998dbcceec1abaf83ab26294d278d25527e779050ab334c1fadab16c'))
  AND (SELECT max(slot) FROM cursor_ WHERE id = 1) > 200136161
  AND NOT EXISTS (SELECT 1 FROM cursor_
                   WHERE id = 1 AND block_hash = '0acc389e7ce34b5ed78537d9e96e21ad6cde8422fc143e27439ff77bfef2cdc5');

DELETE FROM cursor_
WHERE id = 1 AND slot > 200136161
  AND EXISTS (SELECT 1 FROM era
               WHERE block_hash IN ('aa83acbf5904c0edfe4d79b3689d3d00fcfc553cf360fd2229b98d464c28e9de',
                                    '9aa420cf998dbcceec1abaf83ab26294d278d25527e779050ab334c1fadab16c'));
