-- Performance & security review fixes (REVIEW-PERF-SECURITY.md, 2026-11-09). MariaDB 10.5+.
--
-- Apply BEFORE deploying the backend: production runs with
-- quarkus.hibernate-orm.schema-management.strategy=validate and does not start without the new
-- SECRET columns. The old backend ignores them, so the script can be applied while it is running.
--   mariadb wildrovers < 2026-11-09_perf_security_review.sql
-- The script is safe to re-run.

-- A-S5: wrong verification codes per issued code; the code is void after five.
ALTER TABLE SECRET ADD COLUMN IF NOT EXISTS verificationAttempts INT NOT NULL DEFAULT 0;

-- A-S1: e-mail changes wait for a code sent to the new address.
ALTER TABLE SECRET ADD COLUMN IF NOT EXISTS pendingEmail VARCHAR(320) NULL;
ALTER TABLE SECRET ADD COLUMN IF NOT EXISTS pendingEmailCode VARCHAR(64) NULL;
ALTER TABLE SECRET ADD COLUMN IF NOT EXISTS pendingEmailTimestamp BIGINT NULL;
ALTER TABLE SECRET ADD COLUMN IF NOT EXISTS pendingEmailAttempts INT NOT NULL DEFAULT 0;

-- A-P6: the quota counts mails of the last hour (per type) and per recipient of the last day.
-- Recipients are stored lower-case, so the query no longer needs LOWER() and can use the index.
UPDATE EMAIL_SEND_LOG SET recipient = LOWER(recipient) WHERE recipient <> LOWER(recipient);
ALTER TABLE EMAIL_SEND_LOG ADD INDEX IF NOT EXISTS IDX_EMAIL_SEND_LOG_CREATED (created_at);
ALTER TABLE EMAIL_SEND_LOG ADD INDEX IF NOT EXISTS IDX_EMAIL_SEND_LOG_RECIPIENT_CREATED (recipient, created_at);
-- The nightly purge keeps two days; drop the backlog once.
DELETE FROM EMAIL_SEND_LOG WHERE created_at < (UNIX_TIMESTAMP() - 2 * 86400) * 1000;

-- A-S6: accounts soft-deleted before this release still hold personal data. Clear it the same
-- way the backend now does on deletion (blocked + inactive + names already set to "Gelöscht").
DELETE p FROM PHONE p JOIN USER u ON u.id = p.user_id
    WHERE u.isBlocked = 1 AND u.isActive = 0 AND u.firstName = 'Gelöscht' AND u.lastName = 'Gelöscht';
DELETE a FROM Address a JOIN USER u ON u.id = a.userAddress_id
    WHERE u.isBlocked = 1 AND u.isActive = 0 AND u.firstName = 'Gelöscht' AND u.lastName = 'Gelöscht';
DELETE s FROM AUTH_SESSION s JOIN USER u ON u.id = s.user_id
    WHERE u.isBlocked = 1 AND u.isActive = 0 AND u.firstName = 'Gelöscht' AND u.lastName = 'Gelöscht';
UPDATE USER SET
        email = CONCAT('geloescht-', id, '@deleted.invalid'),
        userName = CONCAT('geloescht-', id),
        birthday = NULL, lastLoginIp = NULL, lastLoginCountry = NULL, ribbon = NULL
    WHERE isBlocked = 1 AND isActive = 0 AND firstName = 'Gelöscht' AND lastName = 'Gelöscht'
      AND email NOT LIKE '%@deleted.invalid';
-- Their photo files (<id>.jpg, <id>_background.jpg in the user-photos and profile-backgrounds
-- upload directories) are no longer served; delete them by hand, see the list:
--   SELECT id FROM USER WHERE email LIKE '%@deleted.invalid';
