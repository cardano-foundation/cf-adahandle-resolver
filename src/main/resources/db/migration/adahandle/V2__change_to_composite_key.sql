DROP INDEX IF EXISTS idx_ada_handle_history_item_slot;

ALTER TABLE ada_handle_history_item DROP CONSTRAINT IF EXISTS ada_handle_history_item_pkey;
ALTER TABLE ada_handle_history_item DROP COLUMN IF EXISTS id;
ALTER TABLE ada_handle_history_item ADD PRIMARY KEY (name, slot);

CREATE INDEX idx_ada_handle_history_item_slot ON ada_handle_history_item(slot);