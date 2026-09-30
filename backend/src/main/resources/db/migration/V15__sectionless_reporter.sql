-- The newspaper-wide marker "reporter without a section": the account may use the media endpoints
-- without writing articles. Kept in the Presserl database (not in Keycloak), so it acts at once.
-- account_id and assigned_by are Keycloak user ids (token sub).
CREATE TABLE sectionless_reporter (
    account_id  TEXT        PRIMARY KEY,
    assigned_by TEXT        NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL
);
