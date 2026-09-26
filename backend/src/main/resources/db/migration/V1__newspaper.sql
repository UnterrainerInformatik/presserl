-- The newspaper singleton. Stores overrides only (configuration layer 3);
-- NULL columns and missing settings keys fall back to the deployment/code default.
CREATE TABLE newspaper (
    id       BIGINT PRIMARY KEY CHECK (id = 1),
    name     TEXT,
    subtitle TEXT,
    settings JSONB NOT NULL DEFAULT '{}'
);

INSERT INTO newspaper (id) VALUES (1);
