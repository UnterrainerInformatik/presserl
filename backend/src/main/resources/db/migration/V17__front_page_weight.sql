-- Front-page weight: article metadata set by the editor-in-chief or publisher, not content (no
-- revision, no approval). Weighted articles lead the reader's front page, lowest weight first; NULL
-- is no weight. Written with a targeted UPDATE so the optimistic-concurrency version stays unchanged.
ALTER TABLE article
    ADD COLUMN front_page_weight INT,
    ADD CONSTRAINT article_front_page_weight CHECK (front_page_weight BETWEEN 1 AND 999);
