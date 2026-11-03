-- 회원 비활성화 사유 열을 추가한다.
-- 열 추가를 다시 실행해도 이미 있는 열은 유지한다.
-- 기존 회원 행의 상태는 변경하지 않으며, 사유는 NULL로 둔다.

SET @deactivation_reason_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'member'
      AND COLUMN_NAME = 'deactivation_reason'
);
SET @deactivation_reason_sql = IF(
    @deactivation_reason_missing = 0,
    'ALTER TABLE member ADD COLUMN deactivation_reason VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE deactivation_reason_stmt FROM @deactivation_reason_sql;
EXECUTE deactivation_reason_stmt;
DEALLOCATE PREPARE deactivation_reason_stmt;

SHOW COLUMNS FROM member LIKE 'deactivation_reason';

SELECT
    COUNT(*) AS total_members,
    SUM(status = 'INACTIVE') AS inactive_members,
    SUM(deactivation_reason IS NOT NULL) AS members_with_reason
FROM member;
