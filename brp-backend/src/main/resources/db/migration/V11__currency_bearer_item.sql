-- V11: currencies gain bearer_item_id (docs/03 sections 8-9).
-- NULL = pure ledger currency; non-NULL = the item players can trade
-- face-to-face — the mint window dispenses it AFTER the FAUCET journal
-- books. The legacy-plugin mirror path (section 8) is untouched: adapters
-- book through exchange journals, not this column.
ALTER TABLE currencies ADD COLUMN bearer_item_id VARCHAR(255);
