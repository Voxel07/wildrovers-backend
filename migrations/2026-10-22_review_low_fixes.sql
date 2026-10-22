-- Apply AFTER 2026-10-22_review_medium_fixes.sql and BEFORE deploying the backend.
-- MariaDB 10.5+. Back up the database first. Safe to re-run.
-- Old answer edit dates used the server's local timezone (UTC in the shipped container).
-- If the old JVM used another timezone, change this offset/zone before running.
SET @review_previous_time_zone = @@session.time_zone;
SET SESSION time_zone = '+00:00';
SET @review_legacy_answer_zone = '+00:00';

SET @review_answer_is_text = (
    SELECT DATA_TYPE IN ('varchar', 'text', 'longtext', 'char')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'FORUM_ANSWERS' AND COLUMN_NAME = 'editDate');

-- Retain the original strings, including dates whose source timezone is uncertain.
CREATE TABLE IF NOT EXISTS REVIEW_20261022_ANSWER_EDIT_DATES (
    id BIGINT PRIMARY KEY, editDate VARCHAR(255) NULL);
INSERT IGNORE INTO REVIEW_20261022_ANSWER_EDIT_DATES (id, editDate)
    SELECT id, editDate FROM FORUM_ANSWERS WHERE @review_answer_is_text;

-- Stop on unknown non-empty formats instead of silently discarding timestamps.
-- Run in batch mode without --force so a validation error stops deployment.
CREATE TEMPORARY TABLE review_answer_date_gate (invalid_count INT CHECK (invalid_count = 0));
INSERT INTO review_answer_date_gate
    SELECT COUNT(*) FROM FORUM_ANSWERS
    WHERE @review_answer_is_text AND editDate IS NOT NULL AND TRIM(editDate) <> ''
      AND editDate NOT REGEXP '^[0-9]+$'
      AND (editDate NOT REGEXP '^[0-9]{2}-[0-9]{2}-[0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2}$'
           OR CONVERT_TZ(STR_TO_DATE(editDate, '%d-%m-%Y %H:%i:%s'), @review_legacy_answer_zone, '+00:00') IS NULL);
DROP TEMPORARY TABLE review_answer_date_gate;

UPDATE FORUM_ANSWERS SET editDate = CASE
    WHEN editDate IS NULL OR TRIM(editDate) = '' THEN NULL
    WHEN editDate REGEXP '^[0-9]+$' THEN editDate
    ELSE ROUND(UNIX_TIMESTAMP(CONVERT_TZ(STR_TO_DATE(editDate, '%d-%m-%Y %H:%i:%s'),
                    @review_legacy_answer_zone, '+00:00')) * 1000)
    END
    WHERE @review_answer_is_text;
ALTER TABLE FORUM_ANSWERS MODIFY COLUMN editDate BIGINT NULL;

-- Archive the obsolete picture table without foreign keys, then retire it.
-- Runtime images live on disk and are referenced by post/answer HTML.
SET @review_has_pictures = EXISTS (
    SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'FORUM_PICTURE');
SET @review_sql = IF(@review_has_pictures,
    'CREATE TABLE IF NOT EXISTS REVIEW_20261022_FORUM_PICTURE AS SELECT * FROM FORUM_PICTURE', 'SELECT 1');
PREPARE review_stmt FROM @review_sql;
EXECUTE review_stmt;
DEALLOCATE PREPARE review_stmt;
DROP TABLE IF EXISTS FORUM_PICTURE;

SET SESSION time_zone = @review_previous_time_zone;
