-- Trust. An entry means: this approval level trusts the account and is skipped in the chain of its
-- articles. SECTION_EDITOR entries are per section, the other levels newspaper-wide (section_id
-- NULL). Account and setter are Keycloak user ids, like section_role. The unique constraint also
-- serves lookups by account.
CREATE TABLE trust (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id TEXT        NOT NULL,
    level      TEXT        NOT NULL CHECK (level IN ('SECTION_EDITOR', 'EDITOR_IN_CHIEF', 'PUBLISHER')),
    section_id BIGINT      REFERENCES section (id) ON DELETE CASCADE,
    set_by     TEXT        NOT NULL,
    set_at     TIMESTAMPTZ NOT NULL,
    CHECK ((level = 'SECTION_EDITOR') = (section_id IS NOT NULL)),
    UNIQUE NULLS NOT DISTINCT (account_id, level, section_id)
);
