-- Approval chain. pending_level marks a pending submission: the level the article waits for.
-- A never-published article is SUBMITTED while it waits; a published or offline article keeps its
-- status (and the reader keeps its live revision) while changes wait.
ALTER TABLE article ADD COLUMN pending_level TEXT
    CHECK (pending_level IN ('SECTION_EDITOR', 'EDITOR_IN_CHIEF', 'PUBLISHER'));
ALTER TABLE article ADD CONSTRAINT article_submitted_pending
    CHECK ((status = 'SUBMITTED') = (pending_level IS NOT NULL AND live_revision IS NULL));
CREATE INDEX article_pending_idx ON article (pending_level) WHERE pending_level IS NOT NULL;

-- Approvals and rejections. The reviewer's username and display name are snapshots, like the
-- article's author; only rejections carry a note.
CREATE TABLE article_review (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id            BIGINT      NOT NULL REFERENCES article (id) ON DELETE CASCADE,
    revision              INT         NOT NULL,
    decision              TEXT        NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    level                 TEXT        NOT NULL CHECK (level IN ('SECTION_EDITOR', 'EDITOR_IN_CHIEF', 'PUBLISHER')),
    reviewer_sub          TEXT        NOT NULL,
    reviewer_username     TEXT        NOT NULL,
    reviewer_display_name TEXT        NOT NULL,
    note                  TEXT,
    created_at            TIMESTAMPTZ NOT NULL,
    CHECK ((decision = 'REJECTED') = (note IS NOT NULL))
);
CREATE INDEX article_review_article_idx ON article_review (article_id, created_at DESC);
