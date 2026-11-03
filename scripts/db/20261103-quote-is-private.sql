-- 기존 데이터베이스에 견적 비공개 열을 추가한다.
-- 열 추가를 다시 실행해도 이미 있는 열은 유지한다.
-- 기존 견적 행은 모두 공개(is_private = 0)로 유지한다.

SET @is_private_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quote'
      AND COLUMN_NAME = 'is_private'
);
SET @is_private_sql = IF(
    @is_private_missing = 0,
    'ALTER TABLE quote ADD COLUMN is_private TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE is_private_stmt FROM @is_private_sql;
EXECUTE is_private_stmt;
DEALLOCATE PREPARE is_private_stmt;

SHOW COLUMNS FROM quote LIKE 'is_private';

SELECT
    COUNT(*) AS total_quotes,
    SUM(is_private = 0) AS public_quotes,
    SUM(is_private = 1) AS private_quotes
FROM quote;

SELECT
    COUNT(*) AS total_quotes,
    SUM(is_private = 0) AS public_quotes,
    SUM(is_private = 1) AS private_quotes
FROM quote;