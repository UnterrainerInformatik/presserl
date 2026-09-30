-- Every revision records who wrote it (token subject, username and display name at the time of
-- writing), so corrections by higher levels are attributed and the approval chain can span every
-- contributor. Existing revisions are attributed to their article's author. The V13 trigger fires
-- only on lead_image_media_id and body, so this backfill does not touch article_revision_media.
ALTER TABLE article_revision
    ADD COLUMN author_sub          TEXT,
    ADD COLUMN author_username     TEXT,
    ADD COLUMN author_display_name TEXT;

UPDATE article_revision r
SET author_sub          = a.author_sub,
    author_username     = a.author_username,
    author_display_name = a.author_display_name
FROM article a
WHERE a.id = r.article_id;

ALTER TABLE article_revision
    ALTER COLUMN author_sub SET NOT NULL,
    ALTER COLUMN author_username SET NOT NULL,
    ALTER COLUMN author_display_name SET NOT NULL;
