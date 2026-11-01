-- Indexes for the hot read paths found in the performance analysis (2026-11-01). MariaDB 10.5+.
--
-- Production runs with quarkus.hibernate-orm.schema-management.strategy=validate, which never
-- creates indexes, so they have to be added here. The backend runs with or without them (no
-- startup validation depends on them), so the script can be applied before or after deploying.
-- InnoDB builds secondary indexes online; the tables stay readable and writable meanwhile.
--   mariadb wildrovers < 2026-11-01_performance_indexes.sql
-- The script is safe to re-run.

-- Topic page (posts of a topic, newest first, paged) and the newest post per topic on the
-- forum overview: without it every overview request sorted all posts of all visible topics.
ALTER TABLE FORUM_POSTS ADD INDEX IF NOT EXISTS IDX_FORUM_POSTS_TOPIC_CREATED (topic_id, creationDate, id);

-- Profile/admin statistics: posts per author and the author's latest post (index-only).
ALTER TABLE FORUM_POSTS ADD INDEX IF NOT EXISTS IDX_FORUM_POSTS_USER_CREATED (user_id, creationDate);

-- Answers of a post in creation order, paged: deep pages no longer sort the whole thread.
ALTER TABLE FORUM_ANSWERS ADD INDEX IF NOT EXISTS IDX_FORUM_ANSWERS_POST_CREATED (post_id, creationDate, id);

-- Profile/admin statistics: answers per author and the author's latest answer (index-only).
ALTER TABLE FORUM_ANSWERS ADD INDEX IF NOT EXISTS IDX_FORUM_ANSWERS_USER_CREATED (user_id, creationDate);

-- Year, date-range and upcoming event lists.
ALTER TABLE EVENT ADD INDEX IF NOT EXISTS IDX_EVENT_DATE (event_date);

-- "Events attended" statistics (status = 'YES' per user).
ALTER TABLE EVENT_ATTENDANCES ADD INDEX IF NOT EXISTS IDX_EVENT_ATTENDANCES_USER_STATUS (user_id, status);

ANALYZE TABLE FORUM_POSTS, FORUM_ANSWERS, EVENT, EVENT_ATTENDANCES;
