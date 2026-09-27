-- Every article has a section. Articles still without one (installations that skipped the startup
-- bootstrap since V4) are filed under the first section by position; 'General' (the code default
-- of section.default) is created only when such articles exist but no section does. The literal
-- name cannot follow a configured default name; this path only exists so the ALTER never fails.
INSERT INTO section (name, slug, color, position, created_at)
SELECT 'General', 'general', 'red', 0, now()
WHERE EXISTS (SELECT 1 FROM article WHERE section_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM section);

UPDATE article SET section_id = (SELECT id FROM section ORDER BY position, id LIMIT 1)
WHERE section_id IS NULL;

ALTER TABLE article ALTER COLUMN section_id SET NOT NULL;
