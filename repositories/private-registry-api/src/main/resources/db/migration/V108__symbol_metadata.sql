ALTER TABLE symbols
    ALTER COLUMN document_path DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS symbol_type text,
    ADD COLUMN IF NOT EXISTS default_value text,
    ADD COLUMN IF NOT EXISTS is_required boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS sensitive boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN symbols.default_value IS
    'Optional raw HCL expression for the declared default value; it is not evaluated by Registry.';
