-- Which media every article revision uses: its lead image and the media of every image block of its
-- body. The rows are maintained by the trigger below only, as a pure function of the revision row,
-- so every rule built on "the media is used by a revision" (reader route, usage, edit lock) asks
-- one place. A used media cannot be deleted, like a lead image.
CREATE TABLE article_revision_media (
    article_id BIGINT NOT NULL,
    number     INT    NOT NULL,
    media_id   BIGINT NOT NULL REFERENCES media (id) ON DELETE RESTRICT,
    PRIMARY KEY (article_id, number, media_id),
    FOREIGN KEY (article_id, number) REFERENCES article_revision (article_id, number) ON DELETE CASCADE
);
CREATE INDEX article_revision_media_media_idx ON article_revision_media (media_id);

-- Replaces the row set of the revision. The body is validated before any write, so every image
-- block's mediaId is a positive integer.
CREATE FUNCTION article_revision_media_sync() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    DELETE FROM article_revision_media WHERE article_id = NEW.article_id AND number = NEW.number;
    INSERT INTO article_revision_media (article_id, number, media_id)
    SELECT DISTINCT NEW.article_id, NEW.number, used.media_id
    FROM (SELECT NEW.lead_image_media_id AS media_id
          UNION ALL
          SELECT (image.value #>> '{}')::BIGINT
          FROM jsonb_path_query(NEW.body, '$.blocks[*] ? (@.type == "image").mediaId') AS image (value)) used
    WHERE used.media_id IS NOT NULL;
    RETURN NULL;
END;
$$;

CREATE TRIGGER article_revision_media_sync
    AFTER INSERT OR UPDATE OF lead_image_media_id, body ON article_revision
    FOR EACH ROW EXECUTE FUNCTION article_revision_media_sync();

-- Existing revisions can only use media as lead image yet.
INSERT INTO article_revision_media (article_id, number, media_id)
SELECT article_id, number, lead_image_media_id
FROM article_revision
WHERE lead_image_media_id IS NOT NULL;
