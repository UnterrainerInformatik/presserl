-- Smaller copies of every media, derived from the stored image (same format, no metadata). Each
-- rendition is its own object in the media bucket, also when it has the size of the stored image.
CREATE TABLE media_rendition (
    media_id     BIGINT NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    kind         TEXT   NOT NULL CHECK (kind IN ('thumbnail', 'web', 'print')),
    object_key   TEXT   NOT NULL UNIQUE,
    content_type TEXT   NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    width        INT    NOT NULL CHECK (width > 0),
    height       INT    NOT NULL CHECK (height > 0),
    byte_size    BIGINT NOT NULL CHECK (byte_size > 0),
    PRIMARY KEY (media_id, kind)
);

-- The optional lead image is revision content: it goes live with the revision that carries it.
-- A caption needs an image; a referenced media cannot be deleted.
ALTER TABLE article_revision
    ADD COLUMN lead_image_media_id BIGINT REFERENCES media (id) ON DELETE RESTRICT,
    ADD COLUMN lead_image_caption  TEXT NOT NULL DEFAULT '',
    ADD CONSTRAINT article_revision_caption_needs_image
        CHECK (lead_image_media_id IS NOT NULL OR lead_image_caption = '');
CREATE INDEX article_revision_lead_image_idx ON article_revision (lead_image_media_id)
    WHERE lead_image_media_id IS NOT NULL;
