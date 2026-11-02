-- A burn is recorded as a history row without addresses, so that a rollback past the burn
-- restores the handle's previous owner.
ALTER TABLE ada_handle_history_item ADD COLUMN burned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ada_handle_history_item ALTER COLUMN payment_address DROP NOT NULL;
