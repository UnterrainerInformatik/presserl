-- Sections and per-section roles. Newspaper-wide roles stay Keycloak groups; section roles live
-- here, keyed by the Keycloak user id (token sub). Colours are palette keys, mapped to theme tokens
-- by the reader. Positions are rewritten as a whole on reorder, so they carry no unique constraint;
-- ties sort by id.
CREATE TABLE section (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT        NOT NULL,
    slug       TEXT        NOT NULL UNIQUE,
    color      TEXT        NOT NULL CHECK (color IN ('red', 'orange', 'yellow', 'green', 'teal', 'blue', 'purple', 'pink')),
    position   INT         NOT NULL,
    settings   JSONB       NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX section_name_lower_idx ON section (lower(name));

-- One role per account and section; roles are cumulative, so SECTION_EDITOR includes REPORTER.
CREATE TABLE section_role (
    section_id  BIGINT      NOT NULL REFERENCES section (id) ON DELETE CASCADE,
    account_id  TEXT        NOT NULL,
    role        TEXT        NOT NULL CHECK (role IN ('SECTION_EDITOR', 'REPORTER')),
    assigned_by TEXT        NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (section_id, account_id)
);
CREATE INDEX section_role_account_idx ON section_role (account_id);
