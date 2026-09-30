-- Description and free tags of a media. A tag exists exactly while a row carries it; name keeps the
-- spelling as stored, name_key = lower(name) (computed by the backend) is the comparison key.
ALTER TABLE media ADD COLUMN description TEXT;

CREATE TABLE media_tag (
    media_id BIGINT NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    name     TEXT   NOT NULL,
    name_key TEXT   NOT NULL,
    PRIMARY KEY (media_id, name_key)
);

CREATE INDEX media_tag_name_key ON media_tag (name_key text_pattern_ops);
