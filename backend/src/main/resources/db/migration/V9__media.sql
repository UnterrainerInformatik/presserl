-- Uploaded images. The re-encoded image lives in the object store under object_key; the uploader is
-- identified by the token subject, username and display name are snapshots like article bylines.
CREATE TABLE media (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_key            TEXT        NOT NULL UNIQUE,
    content_type          TEXT        NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    width                 INT         NOT NULL CHECK (width > 0),
    height                INT         NOT NULL CHECK (height > 0),
    byte_size             BIGINT      NOT NULL CHECK (byte_size > 0),
    uploader_sub          TEXT        NOT NULL,
    uploader_username     TEXT        NOT NULL,
    uploader_display_name TEXT        NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL
);
