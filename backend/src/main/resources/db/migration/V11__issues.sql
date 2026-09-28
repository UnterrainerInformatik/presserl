-- Issues group articles. The number is assigned once (next after the highest) and never changed;
-- publication_date is display only; published switches the issue live for readers, published_at
-- records the latest switch.
CREATE TABLE issue (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number           INT         NOT NULL UNIQUE CHECK (number > 0),
    publication_date DATE,
    published        BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT issue_published_at CHECK (published = (published_at IS NOT NULL))
);

-- An article belongs to at most one issue. Positions are rewritten 0..n-1 on every reorder and
-- appended with max+1, so they carry no unique constraint; ties sort by article id. The foreign key
-- has no delete action on purpose: deleting an issue clears both columns of its articles first.
ALTER TABLE article
    ADD COLUMN issue_id       BIGINT REFERENCES issue (id),
    ADD COLUMN issue_position INT,
    ADD CONSTRAINT article_issue_position CHECK ((issue_id IS NULL) = (issue_position IS NULL));
CREATE INDEX article_issue_idx ON article (issue_id, issue_position) WHERE issue_id IS NOT NULL;

-- Issue 1, not live; every article published before receives it in order of first publication.
INSERT INTO issue (number) VALUES (1);
UPDATE article a SET issue_id = i.id, issue_position = ranked.pos
  FROM issue i,
       (SELECT id, row_number() OVER (ORDER BY published_at, id) - 1 AS pos
          FROM article WHERE published_at IS NOT NULL) ranked
 WHERE i.number = 1 AND a.id = ranked.id;
