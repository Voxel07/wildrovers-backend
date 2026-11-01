-- Date-based poll options and events created from a poll option. MariaDB 10.5+.
--
-- Apply BEFORE deploying the backend: production runs with
-- quarkus.hibernate-orm.schema-management.strategy=validate and does not start without these
-- columns. The old backend ignores them, so the script can be applied while it is running.
--   mariadb wildrovers < 2026-11-01_poll_date_options.sql
-- The script is safe to re-run.

-- First and last day of a generated date option (both NULL for plain text options,
-- option_end_date NULL for single days).
ALTER TABLE FORUM_POLL_OPTIONS ADD COLUMN IF NOT EXISTS option_date DATE NULL;
ALTER TABLE FORUM_POLL_OPTIONS ADD COLUMN IF NOT EXISTS option_end_date DATE NULL;

-- Event created from the option. No foreign key on purpose (same as EVENT.forum_post_id):
-- deleting the event clears the id in the backend.
ALTER TABLE FORUM_POLL_OPTIONS ADD COLUMN IF NOT EXISTS event_id BIGINT NULL;
ALTER TABLE FORUM_POLL_OPTIONS ADD INDEX IF NOT EXISTS IDX_FORUM_POLL_OPTIONS_EVENT (event_id);
