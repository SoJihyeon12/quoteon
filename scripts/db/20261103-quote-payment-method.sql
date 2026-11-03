-- 기존 데이터베이스에 견적 결제방법 열을 추가한다.
-- 열 추가를 다시 실행해도 이미 있는 열은 유지한다.
-- 기존 견적 행의 payment_method 는 NULL 로 둔다.

SET @payment_method_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quote'
      AND COLUMN_NAME = 'payment_method'
);
SET @payment_method_sql = IF(
    @payment_method_missing = 0,
    'ALTER TABLE quote ADD COLUMN payment_method VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE payment_method_stmt FROM @payment_method_sql;
EXECUTE payment_method_stmt;
DEALLOCATE PREPARE payment_method_stmt;
