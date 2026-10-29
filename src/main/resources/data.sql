-- =========================================
-- QuoteOn 테스트 데이터
-- =========================================

-- 1. 카테고리
INSERT INTO category (name, parent_id)
VALUES
    ('전자제품', NULL),
    ('사무용품', NULL),
    ('가구', NULL);

-- 2. 제품
INSERT INTO product (
    category_id,
    product_code,
    name,
    unit_price,
    sale_price,
    specification,
    description,
    image_path,
    status
)
VALUES
    (
        1,
        'SH-600',
        '화이트 신발장',
        80000.00,
        120000.00,
        '600x330x1828mm',
        '화이트 색상의 신발장',
        '/images/products/sh-600.jpg',
        'ACTIVE'
    ),
    (
        1,
        'SH-800',
        '오크 원목 선반',
        100000.00,
        150000.00,
        '800x300x1800mm',
        '오크 원목 소재의 선반',
        '/images/products/sh-800.jpg',
        'ACTIVE'
    ),
    (
        2,
        'OF-100',
        '사무용 책상',
        90000.00,
        135000.00,
        '1200x600x750mm',
        '기본형 사무용 책상',
        '/images/products/of-100.jpg',
        'ACTIVE'
    );