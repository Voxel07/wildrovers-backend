-- Schema changes for the medium findings of REVIEW.md (2026-10-22). MariaDB 10.5+.
--
-- Production runs with quarkus.hibernate-orm.schema-management.strategy=validate, so this
-- script must be applied BEFORE the new backend version starts (the new EVENT columns are
-- validated at startup). Take a backup first:
--   mariadb-dump --single-transaction wildrovers > backup-before-2026-10-22.sql
-- Then run:
--   mariadb wildrovers < 2026-10-22_review_medium_fixes.sql
-- The script is safe to re-run.

-- ── 2.7 Topic names are unique per category, not globally ──────────────────────────────
-- Drop Hibernate's old single-column unique index on FORUM_TOPIC.topic (its name is generated).
SET @old_topic_index := (
    SELECT s.INDEX_NAME FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'FORUM_TOPIC' AND s.COLUMN_NAME = 'topic'
      AND s.NON_UNIQUE = 0 AND s.INDEX_NAME <> 'PRIMARY'
      AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS o
                      WHERE o.TABLE_SCHEMA = s.TABLE_SCHEMA AND o.TABLE_NAME = s.TABLE_NAME
                        AND o.INDEX_NAME = s.INDEX_NAME AND o.SEQ_IN_INDEX > 1)
    LIMIT 1);
SET @sql := IF(@old_topic_index IS NULL, 'SELECT 1',
               CONCAT('ALTER TABLE FORUM_TOPIC DROP INDEX `', @old_topic_index, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE FORUM_TOPIC ADD UNIQUE INDEX IF NOT EXISTS UK_FORUM_TOPIC_CATEGORY_TOPIC (category_id, topic);

-- ── 2.6 One view and one vote per user and post ─────────────────────────────────────────
-- Remove duplicates from double clicks first: keep the oldest view and the newest vote.
DELETE v1 FROM FORUM_POST_VIEWS v1
    JOIN FORUM_POST_VIEWS v2 ON v1.user_id = v2.user_id AND v1.post_id = v2.post_id AND v1.id > v2.id;
ALTER TABLE FORUM_POST_VIEWS ADD UNIQUE INDEX IF NOT EXISTS UK_FORUM_POST_VIEWS_USER_POST (user_id, post_id);

DELETE v1 FROM FORUM_POST_VOTES v1
    JOIN FORUM_POST_VOTES v2 ON v1.user_id = v2.user_id AND v1.post_id = v2.post_id AND v1.id < v2.id;
ALTER TABLE FORUM_POST_VOTES ADD UNIQUE INDEX IF NOT EXISTS UK_FORUM_POST_VOTES_USER_POST (user_id, post_id);

-- Recount the counters that lost updates (2.4 / 2.6). Views are kept as they are.
UPDATE FORUM_POSTS p SET
    likes = (SELECT COUNT(*) FROM FORUM_POST_VOTES v WHERE v.post_id = p.id AND v.vote_type = 'LIKE'),
    dislikes = (SELECT COUNT(*) FROM FORUM_POST_VOTES v WHERE v.post_id = p.id AND v.vote_type = 'DISLIKE'),
    answerCount = (SELECT COUNT(*) FROM FORUM_ANSWERS a WHERE a.post_id = p.id);
UPDATE FORUM_TOPIC t SET postCount = (SELECT COUNT(*) FROM FORUM_POSTS p WHERE p.topic_id = t.id);
UPDATE FORUM_CATEGORY c SET topicCount = (SELECT COUNT(*) FROM FORUM_TOPIC t WHERE t.category_id = c.id);
UPDATE FORUM_POLL_OPTIONS o SET votes = (SELECT COUNT(*) FROM FORUM_POLL_OPTION_VOTES ov WHERE ov.option_id = o.id);

-- ── 3.3 / 3.4 Event ↔ forum post link and calendar sync marker ──────────────────────────
ALTER TABLE EVENT
    ADD COLUMN IF NOT EXISTS forum_post_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS calendar_sync_requested BIGINT NULL;

-- Backfill the link from the stored URL (".../Forum/Post/{id}"), only for posts that still exist.
UPDATE EVENT e
    JOIN FORUM_POSTS p ON p.id = CAST(SUBSTRING_INDEX(e.forum_post_url, '/', -1) AS UNSIGNED)
SET e.forum_post_id = p.id
WHERE e.forum_post_id IS NULL AND e.forum_post_url LIKE '%/Forum/Post/%';

CREATE INDEX IF NOT EXISTS IDX_EVENT_FORUM_POST ON EVENT (forum_post_id);
-- Deleting a post (also by hand in the database) keeps the event and clears the link.
ALTER TABLE EVENT ADD CONSTRAINT FK_EVENT_FORUM_POST
    FOREIGN KEY IF NOT EXISTS (forum_post_id) REFERENCES FORUM_POSTS (id) ON DELETE SET NULL;
