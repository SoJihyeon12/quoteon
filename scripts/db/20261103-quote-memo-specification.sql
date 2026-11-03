-- 기존 데이터베이스에 견적 메모와 규격 스냅샷 열을 추가한다.
-- 열 추가를 다시 실행해도 이미 있는 열은 유지한다.
-- 규격 출처가 없는 기존 항목만 현재 제품 규격으로 채우고 RECOVERED로 표시한다.

SET @quote_memo_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quote'
      AND COLUMN_NAME = 'memo'
);
SET @quote_memo_sql = IF(
    @quote_memo_missing = 0,
    'ALTER TABLE quote ADD COLUMN memo TEXT NULL',
    'SELECT 1'
);
PREPARE quote_memo_stmt FROM @quote_memo_sql;
EXECUTE quote_memo_stmt;
DEALLOCATE PREPARE quote_memo_stmt;

SET @item_specification_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quote_item'
      AND COLUMN_NAME = 'specification'
);
SET @item_specification_sql = IF(
    @item_specification_missing = 0,
    'ALTER TABLE quote_item ADD COLUMN specification VARCHAR(500) NULL',
    'SELECT 1'
);
PREPARE item_specification_stmt FROM @item_specification_sql;
EXECUTE item_specification_stmt;
DEALLOCATE PREPARE item_specification_stmt;

SET @item_source_missing = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quote_item'
      AND COLUMN_NAME = 'specification_source'
);
SET @item_source_sql = IF(
    @item_source_missing = 0,
    'ALTER TABLE quote_item ADD COLUMN specification_source VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE item_source_stmt FROM @item_source_sql;
EXECUTE item_source_stmt;
DEALLOCATE PREPARE item_source_stmt;

UPDATE quote_item qi
INNER JOIN product p ON p.id = qi.product_id
SET qi.specification = p.specification,
    qi.specification_source = 'RECOVERED'
WHERE qi.specification_source IS NULL;
