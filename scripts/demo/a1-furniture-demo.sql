-- 에이원퍼니처 시연 데이터.
-- 애플리케이션 시작 시 실행되지 않는다. schema.sql, data.sql, test-reset.sql 과 무관하다.
-- schema.sql 만 적용한 빈 quoteon_db 에서 실행한다. 회원 5명도 이 파일이 만든다.
-- 애플리케이션 코드는 회원 ID를 고정하지 않으므로 회원 번호는 자동 증가 값이다.
-- 비밀번호는 BCrypt 해시만 저장한다. 평문은 이 파일에 없다.
-- quote_email_history 행은 SMTP 발송 결과가 아니다. 화면 시연용으로 직접 삽입한 가상 기록이다.
-- 데이터베이스 이름이 quoteon_db 가 아니거나 테이블에 행이 있으면 삽입 전에 중단한다.
-- 재실행 시 기존 행을 지우거나 덮어쓰지 않는다.

SET @db_name = DATABASE();
SET @guard = IF(
    @db_name = 'quoteon_db',
    'SELECT 1',
    'SELECT refused FROM quoteon_demo_refused_wrong_database'
);
PREPARE demo_guard FROM @guard;
EXECUTE demo_guard;
DEALLOCATE PREPARE demo_guard;

SET @ready = (
    @db_name = 'quoteon_db'
    AND (SELECT COUNT(*) FROM member) = 0
    AND (SELECT COUNT(*) FROM category) = 0
    AND (SELECT COUNT(*) FROM product) = 0
    AND (SELECT COUNT(*) FROM product_image) = 0
    AND (SELECT COUNT(*) FROM customer) = 0
    AND (SELECT COUNT(*) FROM quote) = 0
    AND (SELECT COUNT(*) FROM quote_item) = 0
    AND (SELECT COUNT(*) FROM quote_email_history) = 0
);
SET @guard = IF(@ready = 1, 'SELECT 1', 'SELECT refused FROM quoteon_demo_refused_precondition');
PREPARE demo_guard FROM @guard;
EXECUTE demo_guard;
DEALLOCATE PREPARE demo_guard;

CREATE TEMPORARY TABLE demo_customer (
    member_email VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    memo VARCHAR(500) NOT NULL
);

INSERT INTO demo_customer (member_email, name, phone, email, address, memo) VALUES
    ('sales1@aonef.com', '한빛건설', '02-6010-1101', 'contact@hanbit-const.example', '서울특별시 중구 세종대로 110', '담당자 김도윤. 본사 사무공간 40석 교체 검토.'),
    ('sales1@aonef.com', '서린바이오', '02-6010-1102', 'contact@seorin-bio.example', '경기도 성남시 분당구 판교로 220', '담당자 정하은. 연구소 임원실 가구.'),
    ('sales1@aonef.com', '북극물류', '02-6010-1103', 'contact@bukgeuk-logi.example', '인천광역시 연수구 컨벤시아대로 80', '담당자 박지후. 물류센터 회의실 신설.'),
    ('sales2@aonef.com', '라온교육', '02-6010-2201', 'contact@raon-edu.example', '서울특별시 마포구 월드컵북로 400', '담당자 윤서아. 강의실 책상과 의자 교체.'),
    ('sales2@aonef.com', '다온시스템', '02-6010-2202', 'contact@daon-sys.example', '서울특별시 금천구 가산디지털1로 150', '담당자 장민재. 개발실 좌석 확장.'),
    ('sales2@aonef.com', '푸른법무', '02-6010-2203', 'contact@pureun-law.example', '서울특별시 서초구 서초대로 300', '담당자 서지안. 응접실과 임원실.'),
    ('sales3@aonef.com', '모던리빙', '02-6010-3301', 'contact@modern-living.example', '서울특별시 강남구 테헤란로 500', '담당자 한예진. 쇼룸 휴게 공간.'),
    ('sales3@aonef.com', '세빛연구소', '02-6010-3302', 'contact@sebit-lab.example', '대전광역시 유성구 대학로 90', '담당자 조현우. 연구동 업무 좌석.'),
    ('sales3@aonef.com', '이음파트너스', '02-6010-3303', 'contact@ieum-partners.example', '서울특별시 영등포구 여의대로 24', '담당자 배수아. 회의실 증설.'),
    ('sales4@aonef.com', '온기헬스케어', '02-6010-4401', 'contact@ongi-health.example', '서울특별시 송파구 올림픽로 300', '담당자 문지호. 상담실과 사무 좌석.'),
    ('sales4@aonef.com', '케이브디자인', '02-6010-4402', 'contact@cave-design.example', '서울특별시 성동구 왕십리로 80', '담당자 신유진. 스튜디오 회의 공간.'),
    ('sales4@aonef.com', '하람엔지니어링', '02-6010-4403', 'contact@haram-eng.example', '경기도 안양시 동안구 시민대로 200', '담당자 류태양. 문서고와 파티션.');


CREATE TEMPORARY TABLE demo_quote (
    quote_number VARCHAR(100) NOT NULL,
    member_email VARCHAR(255) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    quote_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL
);

INSERT INTO demo_quote (quote_number, member_email, customer_name, quote_date, status) VALUES
    ('Q-20261008-a1f0000000000001', 'sales1@aonef.com', '한빛건설', '2026-10-08', 'DRAFT'),
    ('Q-20261020-a1f0000000000002', 'sales1@aonef.com', '한빛건설', '2026-10-20', 'SENT'),
    ('Q-20261101-a1f0000000000003', 'sales1@aonef.com', '한빛건설', '2026-11-01', 'DRAFT'),
    ('Q-20260915-a1f0000000000004', 'sales1@aonef.com', '서린바이오', '2026-09-15', 'SENT'),
    ('Q-20261028-a1f0000000000005', 'sales1@aonef.com', '서린바이오', '2026-10-28', 'DRAFT'),
    ('Q-20261003-a1f0000000000006', 'sales1@aonef.com', '북극물류', '2026-10-03', 'DRAFT'),
    ('Q-20261102-a1f0000000000007', 'sales1@aonef.com', '북극물류', '2026-11-02', 'DRAFT'),
    ('Q-20260922-a1f0000000000008', 'sales2@aonef.com', '라온교육', '2026-09-22', 'SENT'),
    ('Q-20261014-a1f0000000000009', 'sales2@aonef.com', '라온교육', '2026-10-14', 'DRAFT'),
    ('Q-20261101-a1f000000000000a', 'sales2@aonef.com', '라온교육', '2026-11-01', 'DRAFT'),
    ('Q-20261006-a1f000000000000b', 'sales2@aonef.com', '다온시스템', '2026-10-06', 'SENT'),
    ('Q-20261027-a1f000000000000c', 'sales2@aonef.com', '다온시스템', '2026-10-27', 'DRAFT'),
    ('Q-20260930-a1f000000000000d', 'sales2@aonef.com', '푸른법무', '2026-09-30', 'DRAFT'),
    ('Q-20261009-a1f000000000000e', 'sales3@aonef.com', '모던리빙', '2026-10-09', 'SENT'),
    ('Q-20261024-a1f000000000000f', 'sales3@aonef.com', '모던리빙', '2026-10-24', 'DRAFT'),
    ('Q-20260918-a1f0000000000010', 'sales3@aonef.com', '세빛연구소', '2026-09-18', 'SENT'),
    ('Q-20261102-a1f0000000000011', 'sales3@aonef.com', '세빛연구소', '2026-11-02', 'DRAFT'),
    ('Q-20261016-a1f0000000000012', 'sales3@aonef.com', '이음파트너스', '2026-10-16', 'DRAFT'),
    ('Q-20261030-a1f0000000000013', 'sales3@aonef.com', '이음파트너스', '2026-10-30', 'SENT'),
    ('Q-20260925-a1f0000000000014', 'sales4@aonef.com', '온기헬스케어', '2026-09-25', 'SENT'),
    ('Q-20261021-a1f0000000000015', 'sales4@aonef.com', '온기헬스케어', '2026-10-21', 'DRAFT'),
    ('Q-20261011-a1f0000000000016', 'sales4@aonef.com', '케이브디자인', '2026-10-11', 'DRAFT'),
    ('Q-20261101-a1f0000000000017', 'sales4@aonef.com', '케이브디자인', '2026-11-01', 'SENT'),
    ('Q-20261002-a1f0000000000018', 'sales4@aonef.com', '하람엔지니어링', '2026-10-02', 'DRAFT'),
    ('Q-20261029-a1f0000000000019', 'sales4@aonef.com', '하람엔지니어링', '2026-10-29', 'DRAFT');


CREATE TEMPORARY TABLE demo_line (
    quote_number VARCHAR(100) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    quantity INT NOT NULL
);

INSERT INTO demo_line (quote_number, product_code, quantity) VALUES
    ('Q-20261008-a1f0000000000001', 'A1-DSK-1400', 4),
    ('Q-20261008-a1f0000000000001', 'A1-CHR-MESH', 4),
    ('Q-20261020-a1f0000000000002', 'A1-DSK-1600', 6),
    ('Q-20261020-a1f0000000000002', 'A1-CHR-HEAD', 6),
    ('Q-20261020-a1f0000000000002', 'A1-PTN-1200', 6),
    ('Q-20261101-a1f0000000000003', 'A1-MTG-2400', 1),
    ('Q-20261101-a1f0000000000003', 'A1-MTG-CHR', 8),
    ('Q-20261101-a1f0000000000003', 'A1-STG-LOW', 2),
    ('Q-20260915-a1f0000000000004', 'A1-DSK-EXL', 1),
    ('Q-20260915-a1f0000000000004', 'A1-CHR-EXL', 1),
    ('Q-20260915-a1f0000000000004', 'A1-STG-HIGH', 1),
    ('Q-20260915-a1f0000000000004', 'A1-SOF-1', 1),
    ('Q-20261028-a1f0000000000005', 'A1-DSK-1800', 2),
    ('Q-20261028-a1f0000000000005', 'A1-CHR-MESH', 2),
    ('Q-20261003-a1f0000000000006', 'A1-STG-BOOK', 3),
    ('Q-20261003-a1f0000000000006', 'A1-STG-LOW', 2),
    ('Q-20261102-a1f0000000000007', 'A1-MTG-3200', 1),
    ('Q-20261102-a1f0000000000007', 'A1-MTG-CHR', 12),
    ('Q-20261102-a1f0000000000007', 'A1-PTN-FLOOR', 2),
    ('Q-20260922-a1f0000000000008', 'A1-DSK-1400', 10),
    ('Q-20260922-a1f0000000000008', 'A1-CHR-MESH', 10),
    ('Q-20260922-a1f0000000000008', 'A1-PTN-1200', 10),
    ('Q-20261014-a1f0000000000009', 'A1-MTG-RND', 2),
    ('Q-20261014-a1f0000000000009', 'A1-MTG-CHR', 8),
    ('Q-20261101-a1f000000000000a', 'A1-CAF-TABLE', 4),
    ('Q-20261101-a1f000000000000a', 'A1-CAF-CHAIR', 16),
    ('Q-20261006-a1f000000000000b', 'A1-DSK-1600', 8),
    ('Q-20261006-a1f000000000000b', 'A1-CHR-HEAD', 8),
    ('Q-20261006-a1f000000000000b', 'A1-DSK-SIDE', 4),
    ('Q-20261027-a1f000000000000c', 'A1-STG-HIGH', 2),
    ('Q-20261027-a1f000000000000c', 'A1-STG-BOOK', 2),
    ('Q-20260930-a1f000000000000d', 'A1-DSK-EXL', 2),
    ('Q-20260930-a1f000000000000d', 'A1-CHR-EXL', 2),
    ('Q-20260930-a1f000000000000d', 'A1-RCV-TABLE', 1),
    ('Q-20260930-a1f000000000000d', 'A1-SOF-3', 1),
    ('Q-20261009-a1f000000000000e', 'A1-SOF-3', 2),
    ('Q-20261009-a1f000000000000e', 'A1-SOF-1', 4),
    ('Q-20261009-a1f000000000000e', 'A1-RCV-TABLE', 2),
    ('Q-20261024-a1f000000000000f', 'A1-CAF-TABLE', 6),
    ('Q-20261024-a1f000000000000f', 'A1-CAF-CHAIR', 12),
    ('Q-20261024-a1f000000000000f', 'A1-PTN-FLOOR', 1),
    ('Q-20260918-a1f0000000000010', 'A1-DSK-1800', 4),
    ('Q-20260918-a1f0000000000010', 'A1-CHR-HEAD', 4),
    ('Q-20260918-a1f0000000000010', 'A1-STG-LOW', 4),
    ('Q-20261102-a1f0000000000011', 'A1-MTG-HUB', 2),
    ('Q-20261102-a1f0000000000011', 'A1-CHR-STOOL', 6),
    ('Q-20261016-a1f0000000000012', 'A1-DSK-1400', 3),
    ('Q-20261016-a1f0000000000012', 'A1-CHR-VIS', 6),
    ('Q-20261016-a1f0000000000012', 'A1-STG-BOOK', 1),
    ('Q-20261030-a1f0000000000013', 'A1-MTG-2400', 1),
    ('Q-20261030-a1f0000000000013', 'A1-MTG-CHR', 6),
    ('Q-20261030-a1f0000000000013', 'A1-DSK-SIDE', 2),
    ('Q-20260925-a1f0000000000014', 'A1-DSK-1600', 5),
    ('Q-20260925-a1f0000000000014', 'A1-CHR-MESH', 5),
    ('Q-20260925-a1f0000000000014', 'A1-PTN-1200', 5),
    ('Q-20260925-a1f0000000000014', 'A1-STG-LOW', 2),
    ('Q-20261021-a1f0000000000015', 'A1-SOF-1', 2),
    ('Q-20261021-a1f0000000000015', 'A1-RCV-TABLE', 1),
    ('Q-20261011-a1f0000000000016', 'A1-DSK-EXL', 1),
    ('Q-20261011-a1f0000000000016', 'A1-CHR-EXL', 1),
    ('Q-20261011-a1f0000000000016', 'A1-SOF-3', 1),
    ('Q-20261101-a1f0000000000017', 'A1-MTG-RND', 1),
    ('Q-20261101-a1f0000000000017', 'A1-MTG-CHR', 4),
    ('Q-20261101-a1f0000000000017', 'A1-CAF-CHAIR', 4),
    ('Q-20261002-a1f0000000000018', 'A1-STG-HIGH', 3),
    ('Q-20261002-a1f0000000000018', 'A1-STG-BOOK', 2),
    ('Q-20261002-a1f0000000000018', 'A1-PTN-FLOOR', 2),
    ('Q-20261029-a1f0000000000019', 'A1-DSK-1800', 3),
    ('Q-20261029-a1f0000000000019', 'A1-CHR-HEAD', 3),
    ('Q-20261029-a1f0000000000019', 'A1-CHR-VIS', 3);


START TRANSACTION;

INSERT INTO member (email, password, name, phone, company_name, role, status, created_at, updated_at)
VALUES
    ('admin1@aonef.com', '$2a$10$TW.G5vltG1OPbbTAKDwwv.sYBtXWe6a8DhyInohC5rhUeMS74elE.', '김관리', NULL, '에이원퍼니처', 'ROLE_ADMIN', 'ACTIVE', '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
    ('sales1@aonef.com', '$2a$10$TW.G5vltG1OPbbTAKDwwv.sYBtXWe6a8DhyInohC5rhUeMS74elE.', '김영업', NULL, '에이원퍼니처', 'ROLE_USER', 'ACTIVE', '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
    ('sales2@aonef.com', '$2a$10$TW.G5vltG1OPbbTAKDwwv.sYBtXWe6a8DhyInohC5rhUeMS74elE.', '박서연', '010-3201-2201', '에이원퍼니처', 'ROLE_USER', 'ACTIVE', '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
    ('sales3@aonef.com', '$2a$10$TW.G5vltG1OPbbTAKDwwv.sYBtXWe6a8DhyInohC5rhUeMS74elE.', '이준호', '010-3201-2202', '에이원퍼니처', 'ROLE_USER', 'ACTIVE', '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
    ('sales4@aonef.com', '$2a$10$TW.G5vltG1OPbbTAKDwwv.sYBtXWe6a8DhyInohC5rhUeMS74elE.', '최민아', '010-3201-2203', '에이원퍼니처', 'ROLE_USER', 'ACTIVE', '2026-09-01 09:00:00', '2026-09-01 09:00:00');

INSERT INTO category (name, parent_id) VALUES
    ('사무용 책상', NULL),
    ('사무용 의자', NULL),
    ('회의 가구', NULL),
    ('수납·파티션', NULL),
    ('휴게·응접', NULL);

INSERT INTO product (category_id, product_code, name, unit_price, sale_price, specification, description, image_path, status)
SELECT c.id, v.product_code, v.name, v.unit_price, v.sale_price, v.specification, v.description, CONCAT('/images/products/', v.product_code, '.jpg'), 'ACTIVE'
FROM (
    SELECT '사무용 책상' AS category_name, 'A1-DSK-1400' AS product_code, '일자형 사무 책상 1400' AS name, 380000 AS unit_price, 520000 AS sale_price, 'W1400×D700×H720mm' AS specification, '멜라민 상판과 스틸 다리. 1인 일반 업무용' AS description
    UNION ALL SELECT '사무용 책상', 'A1-DSK-1600', '일자형 사무 책상 1600', 450000, 620000, 'W1600×D700×H720mm', '듀얼 모니터를 올릴 수 있는 와이드 상판'
    UNION ALL SELECT '사무용 책상', 'A1-DSK-1800', '확장형 사무 책상 1800', 620000, 860000, 'W1800×D750×H720mm', '측면 보조 상판이 있는 팀 좌석용 책상'
    UNION ALL SELECT '사무용 책상', 'A1-DSK-EXL', '임원용 우드 책상 1800', 980000, 1350000, 'W1800×D800×H750mm', '월넛 무늬 상판과 전선 덮개가 있는 임원실 책상'
    UNION ALL SELECT '사무용 책상', 'A1-DSK-SIDE', '사이드 보조 책상', 240000, 330000, 'W1000×D450×H720mm', '본 책상 옆에 두는 복귀형 보조 상판'
    UNION ALL SELECT '사무용 의자', 'A1-CHR-MESH', '메쉬 사무 의자', 190000, 260000, 'W660×D640×H980~1080mm', '통기 메쉬 등판과 틸트 기능이 있는 업무 의자'
    UNION ALL SELECT '사무용 의자', 'A1-CHR-HEAD', '헤드레스트 사무 의자', 280000, 390000, 'W680×D660×H1140~1240mm', '머리 받침과 요추 지지가 있는 장시간 업무용 의자'
    UNION ALL SELECT '사무용 의자', 'A1-CHR-EXL', '임원용 가죽 의자', 540000, 750000, 'W720×D700×H1160~1260mm', '인조가죽 시트와 우드 팔걸이가 있는 임원용 의자'
    UNION ALL SELECT '사무용 의자', 'A1-CHR-VIS', '손님용 방문 의자', 120000, 165000, 'W560×D580×H820mm', '팔걸이가 없는 4다리 방문객용 의자'
    UNION ALL SELECT '사무용 의자', 'A1-CHR-STOOL', '높이조절 작업 스툴', 150000, 210000, 'W480×D480×H520~720mm', '좌판 높이를 조절하는 작업·상담용 스툴'
    UNION ALL SELECT '회의 가구', 'A1-MTG-2400', '회의 테이블 2400', 820000, 1140000, 'W2400×D1200×H740mm', '6~8인이 앉는 사각 회의 테이블'
    UNION ALL SELECT '회의 가구', 'A1-MTG-3200', '회의 테이블 3200', 1280000, 1780000, 'W3200×D1400×H740mm', '10~12인이 앉는 대형 회의 테이블'
    UNION ALL SELECT '회의 가구', 'A1-MTG-CHR', '회의용 팔걸이 의자', 170000, 235000, 'W600×D580×H900mm', '쌓아 보관할 수 있는 회의실 팔걸이 의자'
    UNION ALL SELECT '회의 가구', 'A1-MTG-RND', '원형 협의 테이블', 460000, 640000, 'Ø1100×H740mm', '4인 소규모 협의용 원형 테이블'
    UNION ALL SELECT '회의 가구', 'A1-MTG-HUB', '스탠딩 허들 테이블', 390000, 540000, 'W1400×D700×H1050mm', '짧은 스탠딩 회의용 높은 테이블'
    UNION ALL SELECT '수납·파티션', 'A1-STG-LOW', '낮은 수납장 3단', 290000, 400000, 'W800×D400×H1100mm', '도어 3칸의 좌석 옆 수납장'
    UNION ALL SELECT '수납·파티션', 'A1-STG-HIGH', '높은 양문 수납장', 520000, 720000, 'W900×D450×H1800mm', '문서와 비품을 보관하는 키 큰 양문장'
    UNION ALL SELECT '수납·파티션', 'A1-STG-BOOK', '오픈 책장 5단', 340000, 470000, 'W900×D350×H1800mm', '문이 없는 5단 오픈 책장'
    UNION ALL SELECT '수납·파티션', 'A1-PTN-1200', '데스크 파티션 1200', 160000, 220000, 'W1200×T40×H450mm', '책상 위에 올려 좌석을 나누는 패브릭 스크린'
    UNION ALL SELECT '수납·파티션', 'A1-PTN-FLOOR', '바닥형 파티션 1600', 480000, 660000, 'W1600×T60×H1800mm', '캐스터가 달린 이동형 공간 파티션'
    UNION ALL SELECT '휴게·응접', 'A1-SOF-3', '3인 패브릭 소파', 920000, 1280000, 'W2100×D850×H780mm', '휴게실과 응접용 3인용 패브릭 소파'
    UNION ALL SELECT '휴게·응접', 'A1-SOF-1', '1인 라운지 체어', 380000, 530000, 'W780×D780×H760mm', '로비와 휴게 공간용 1인용 라운지 체어'
    UNION ALL SELECT '휴게·응접', 'A1-RCV-TABLE', '응접 테이블', 280000, 390000, 'W1200×D600×H400mm', '소파와 함께 두는 낮은 응접 테이블'
    UNION ALL SELECT '휴게·응접', 'A1-CAF-TABLE', '휴게 원형 테이블', 220000, 310000, 'Ø800×H730mm', '휴게 공간의 4인 이하 원형 테이블'
    UNION ALL SELECT '휴게·응접', 'A1-CAF-CHAIR', '휴게 공간 체어', 140000, 195000, 'W520×D540×H780mm', '목재 다리가 있는 휴게 공간용 체어'
) v
JOIN category c ON c.name = v.category_name AND c.parent_id IS NULL;

INSERT INTO product_image (product_id, image_path, sort_order)
SELECT p.id, CONCAT('/images/products/', p.product_code, '-d1.jpg'), 1
FROM product p
UNION ALL
SELECT p.id, CONCAT('/images/products/', p.product_code, '-d2.jpg'), 2
FROM product p;

INSERT INTO customer (member_id, name, customer_type, phone, email, address, memo, status, created_at, updated_at)
SELECT m.id, d.name, '기업', d.phone, d.email, d.address, d.memo, 'ACTIVE', '2026-09-01 09:30:00', '2026-09-01 09:30:00'
FROM demo_customer d
JOIN member m ON m.email = d.member_email;

INSERT INTO quote (quote_number, member_id, customer_id, supply_amount, vat_amount, total_amount, status, quote_date, created_at, updated_at)
SELECT d.quote_number, m.id, c.id, 0, 0, 0, d.status, d.quote_date,
       TIMESTAMP(d.quote_date, '10:00:00'), TIMESTAMP(d.quote_date, '10:00:00')
FROM demo_quote d
JOIN member m ON m.email = d.member_email
JOIN customer c ON c.name = d.customer_name AND c.member_id = m.id;

INSERT INTO quote_item (
    quote_id, product_id, product_name, product_code, specification, specification_source, sale_price, quantity, amount
)
SELECT q.id, p.id, p.name, p.product_code, p.specification, 'SNAPSHOT', p.sale_price, l.quantity,
       ROUND(p.sale_price * l.quantity, 2)
FROM demo_line l
JOIN quote q ON q.quote_number = l.quote_number
JOIN product p ON p.product_code = l.product_code;

UPDATE quote q
JOIN (
    SELECT quote_id, SUM(amount) AS supply
    FROM quote_item
    GROUP BY quote_id
) s ON s.quote_id = q.id
SET q.supply_amount = s.supply,
    q.vat_amount = ROUND(s.supply * 0.10, 0),
    q.total_amount = s.supply + ROUND(s.supply * 0.10, 0),
    q.updated_at = q.created_at
WHERE q.quote_number LIKE 'Q-%-a1f00000000000%';

INSERT INTO quote_email_history (quote_id, recipient_email, subject, status, failure_reason, sent_at, created_at)
SELECT q.id, c.email, '견적서 송부드립니다', 'SUCCESS', NULL, q.created_at, q.created_at
FROM quote q
JOIN customer c ON c.id = q.customer_id
WHERE q.status = 'SENT'
  AND q.quote_number LIKE 'Q-%-a1f00000000000%';

INSERT INTO quote_email_history (quote_id, recipient_email, subject, status, failure_reason, sent_at, created_at)
SELECT q.id, c.email, '견적서 송부드립니다', 'FAILED', '메일 서버에 연결하지 못했습니다.', NULL, q.created_at
FROM quote q
JOIN customer c ON c.id = q.customer_id
WHERE q.quote_number = 'Q-20261029-a1f0000000000019';

SET @valid = (
    (SELECT COUNT(*) FROM member) = 5
    AND (SELECT COUNT(*) FROM member WHERE email = 'admin1@aonef.com' AND name = '김관리' AND role = 'ROLE_ADMIN' AND status = 'ACTIVE' AND company_name = '에이원퍼니처') = 1
    AND (SELECT COUNT(*) FROM member WHERE email = 'sales1@aonef.com' AND name = '김영업' AND role = 'ROLE_USER' AND status = 'ACTIVE' AND company_name = '에이원퍼니처') = 1
    AND (SELECT COUNT(*) FROM member WHERE email IN ('sales2@aonef.com', 'sales3@aonef.com', 'sales4@aonef.com') AND role = 'ROLE_USER' AND status = 'ACTIVE' AND company_name = '에이원퍼니처') = 3
    AND (SELECT COUNT(*) FROM member WHERE password = '$2a$10$TW.G5vltG1OPbbTAKDwwv.sYBtXWe6a8DhyInohC5rhUeMS74elE.') = 5
    AND (SELECT COUNT(*) FROM category WHERE parent_id IS NULL) = 5
    AND (SELECT COUNT(*) FROM category WHERE parent_id IS NOT NULL) = 0
    AND (SELECT COUNT(*) FROM product) = 25
    AND (SELECT COUNT(DISTINCT product_code) FROM product) = 25
    AND (SELECT COUNT(*) FROM product WHERE status = 'ACTIVE' AND image_path = CONCAT('/images/products/', product_code, '.jpg')) = 25
    AND (SELECT COUNT(*) FROM product_image) = 50
    AND (SELECT COUNT(*) FROM product p WHERE (SELECT COUNT(*) FROM product_image i WHERE i.product_id = p.id AND i.sort_order IN (1, 2)) <> 2) = 0
    AND (SELECT COUNT(*) FROM product_image i JOIN product p ON p.id = i.product_id WHERE i.image_path <> CONCAT('/images/products/', p.product_code, '-d', i.sort_order, '.jpg')) = 0
    AND (SELECT COUNT(*) FROM customer) = 12
    AND (SELECT COUNT(*) FROM quote) = 25
    AND (SELECT COUNT(*) FROM quote_item) = (SELECT COUNT(*) FROM demo_line)
    AND (SELECT COUNT(*) FROM quote q WHERE (SELECT COUNT(*) FROM quote_item i WHERE i.quote_id = q.id) NOT BETWEEN 2 AND 4) = 0
    AND (SELECT COUNT(*) FROM quote_item WHERE amount <> ROUND(sale_price * quantity, 2)) = 0
    AND (SELECT COUNT(*) FROM quote q WHERE q.supply_amount <> (SELECT COALESCE(SUM(i.amount), 0) FROM quote_item i WHERE i.quote_id = q.id) OR q.vat_amount <> ROUND(q.supply_amount * 0.10, 0) OR q.total_amount <> q.supply_amount + q.vat_amount) = 0
    AND (SELECT COUNT(*) FROM quote WHERE status NOT IN ('DRAFT', 'SENT')) = 0
    AND (SELECT COUNT(*) FROM product p LEFT JOIN category c ON c.id = p.category_id WHERE c.id IS NULL) = 0
    AND (SELECT COUNT(*) FROM customer cu LEFT JOIN member m ON m.id = cu.member_id WHERE m.id IS NULL) = 0
    AND (SELECT COUNT(*) FROM quote q LEFT JOIN customer c ON c.id = q.customer_id WHERE c.id IS NULL) = 0
    AND (SELECT COUNT(*) FROM quote q LEFT JOIN member m ON m.id = q.member_id WHERE m.id IS NULL) = 0
    AND (SELECT COUNT(*) FROM quote q JOIN customer c ON c.id = q.customer_id WHERE q.member_id <> c.member_id) = 0
    AND (SELECT COUNT(*) FROM quote_item i LEFT JOIN quote q ON q.id = i.quote_id WHERE q.id IS NULL) = 0
    AND (SELECT COUNT(*) FROM quote_item i LEFT JOIN product p ON p.id = i.product_id WHERE p.id IS NULL OR i.product_name <> p.name OR i.product_code <> p.product_code OR i.sale_price <> p.sale_price OR i.specification_source <> 'SNAPSHOT' OR NOT (i.specification <=> p.specification)) = 0
    AND (SELECT COUNT(*) FROM quote_email_history h LEFT JOIN quote q ON q.id = h.quote_id WHERE q.id IS NULL) = 0
    AND (SELECT COUNT(*) FROM quote_email_history WHERE status = 'SUCCESS') = 9
    AND (SELECT COUNT(*) FROM quote_email_history WHERE status = 'FAILED') = 1
    AND (SELECT COUNT(*) FROM quote_email_history h JOIN quote q ON q.id = h.quote_id WHERE h.status = 'SUCCESS' AND q.status <> 'SENT') = 0
    AND (SELECT COUNT(*) FROM (
        SELECT quote_id, product_id FROM quote_item GROUP BY quote_id, product_id HAVING COUNT(*) > 1
    ) duplicated) = 0
);

SET @guard = IF(@valid <=> 1, 'SELECT 1', 'SELECT refused FROM quoteon_demo_refused_validation');
PREPARE demo_guard FROM @guard;
EXECUTE demo_guard;
DEALLOCATE PREPARE demo_guard;

COMMIT;

SELECT
    (SELECT COUNT(*) FROM member) AS members,
    (SELECT COUNT(*) FROM category) AS categories,
    (SELECT COUNT(*) FROM product) AS products,
    (SELECT COUNT(*) FROM product_image) AS product_images,
    (SELECT COUNT(*) FROM customer) AS customers,
    (SELECT COUNT(*) FROM quote) AS quotes,
    (SELECT COUNT(*) FROM quote_item) AS quote_items,
    (SELECT COUNT(*) FROM quote_email_history) AS email_histories,
    @valid AS validation_ok;
