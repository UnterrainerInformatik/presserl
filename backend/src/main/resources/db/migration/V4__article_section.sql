-- Every article belongs to a section. The column stays nullable: the application always sets it,
-- and the startup default-section bootstrap files articles created before sections existed (the
-- default name and the slug rules live in Java, not here). RESTRICT keeps a section with articles
-- from being deleted.
ALTER TABLE article ADD COLUMN section_id BIGINT REFERENCES section (id) ON DELETE RESTRICT;
CREATE INDEX article_section_id_idx ON article (section_id);
