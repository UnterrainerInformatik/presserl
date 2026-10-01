-- Account deletion. A user requests the deletion of their own account; a publisher deletes it.
-- account_id is the Keycloak user id (token sub), like sectionless_reporter.
CREATE TABLE account_deletion_request (
    account_id   TEXT        PRIMARY KEY,
    requested_at TIMESTAMPTZ NOT NULL
);

-- Deleting an account keeps its content and anonymises it: the username and display-name snapshots
-- of its rows become NULL (NULL = deleted account), the *_sub columns stay.
ALTER TABLE article
    ALTER COLUMN author_username DROP NOT NULL,
    ALTER COLUMN author_display_name DROP NOT NULL;
ALTER TABLE article_revision
    ALTER COLUMN author_username DROP NOT NULL,
    ALTER COLUMN author_display_name DROP NOT NULL;
ALTER TABLE article_review
    ALTER COLUMN reviewer_username DROP NOT NULL,
    ALTER COLUMN reviewer_display_name DROP NOT NULL;
ALTER TABLE media
    ALTER COLUMN uploader_username DROP NOT NULL,
    ALTER COLUMN uploader_display_name DROP NOT NULL;
