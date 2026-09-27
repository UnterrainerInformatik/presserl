-- Emergency brake. An article taken offline by a publisher is locked: its chain ends with the
-- PUBLISHER level until it goes online again or a publisher unlocks it. Only OFFLINE articles are
-- locked.
ALTER TABLE article ADD COLUMN locked BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE article ADD CONSTRAINT article_locked_offline CHECK (NOT locked OR status = 'OFFLINE');
