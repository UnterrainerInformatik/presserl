-- Every edit of a media (crop, pixelation) replaces its image and renditions under the same id and
-- increments the version; reader URLs and ETags carry it.
ALTER TABLE media ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Objects of replaced media images, deleted after the replacing transaction commits; rows that
-- remain (object store down, backend stopped) are retried on start and periodically.
CREATE TABLE media_object_trash (
    object_key TEXT        PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);
