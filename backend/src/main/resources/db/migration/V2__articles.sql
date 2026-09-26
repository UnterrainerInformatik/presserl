-- Articles with numbered revisions. The latest revision is the working revision until it is
-- published; live_revision points to the revision the reader shows (NULL until first publish).
-- SUBMITTED is reserved for the approval chain and not written yet.
CREATE TABLE article (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status               TEXT        NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'PUBLISHED', 'OFFLINE')),
    author_sub           TEXT        NOT NULL,
    author_username      TEXT        NOT NULL,
    author_display_name  TEXT        NOT NULL,
    live_revision        INT,
    published_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX article_updated_at_idx ON article (updated_at DESC);
CREATE INDEX article_author_sub_idx ON article (author_sub);

CREATE TABLE article_revision (
    article_id   BIGINT      NOT NULL REFERENCES article (id) ON DELETE CASCADE,
    number       INT         NOT NULL,
    kicker       TEXT        NOT NULL DEFAULT '',
    headline     TEXT        NOT NULL DEFAULT '',
    subheadline  TEXT        NOT NULL DEFAULT '',
    lead         TEXT        NOT NULL DEFAULT '',
    body         JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    PRIMARY KEY (article_id, number)
);
