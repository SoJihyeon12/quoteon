# 1. 데이터베이스 모델링 ERD 명세서 (QuoteOn)

## 1.1 테이블 명세

### 1.1.1 회원 (member)

| 컬럼명 | 데이터 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 회원 ID |
| email | VARCHAR(255) | NOT NULL, UNIQUE | 이메일 |
| password | VARCHAR(255) | NOT NULL | 암호화된 비밀번호 |
| name | VARCHAR(100) | NOT NULL | 회원 이름 |
| phone | VARCHAR(20) | NULL | 연락처 |
| company_name | VARCHAR(255) | NULL | 회사명 |
| role | VARCHAR(30) | NOT NULL | 회원 권한 |
| status | VARCHAR(20) | NOT NULL | 회원 상태 |
| created_at | DATETIME | NOT NULL | 생성 일시 |
| updated_at | DATETIME | NOT NULL | 수정 일시 |

### 1.1.2 카테고리 (category)

| 컬럼명 | 데이터 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 카테고리 ID |
| name | VARCHAR(100) | NOT NULL | 카테고리명 |
| parent_id | BIGINT | FK, NULL | 상위 카테고리 ID |
| created_at | DATETIME | NOT NULL | 생성 일시 |
| updated_at | DATETIME | NOT NULL | 수정 일시 |

### 1.1.3 제품 (product)

| 컬럼명 | 데이터 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 제품 ID |
| category_id | BIGINT | FK, NOT NULL | 카테고리 ID |
| product_code | VARCHAR(100) | NOT NULL, UNIQUE | 제품 코드 |
| name | VARCHAR(255) | NOT NULL | 제품명 |
| unit_price | DECIMAL(12,2) | NOT NULL | 기준 단가 |
| sale_price | DECIMAL(12,2) | NOT NULL | 실제 판매 가격 |
| specification | VARCHAR(500) | NULL | 제품 규격 |
| description | TEXT | NULL | 제품 설명 |
| image_path | VARCHAR(500) | NULL | 제품 이미지 경로 |
| status | VARCHAR(20) | NOT NULL | 제품 상태 |
| created_at | DATETIME | NOT NULL | 생성 일시 |
| updated_at | DATETIME | NOT NULL | 수정 일시 |

### 1.1.4 고객 (customer)

| 컬럼명 | 데이터 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 고객 ID |
| member_id | BIGINT | FK, NOT NULL | 담당 회원 ID |
| name | VARCHAR(100) | NOT NULL | 고객명 |
| customer_type | VARCHAR(30) | NOT NULL | 고객 유형 |
| phone | VARCHAR(20) | NULL | 연락처 |
| email | VARCHAR(255) | NULL | 이메일 |
| address | VARCHAR(500) | NULL | 주소 |
| memo | TEXT | NULL | 고객 메모 |
| status | VARCHAR(20) | NOT NULL | 고객 상태 |
| created_at | DATETIME | NOT NULL | 생성 일시 |
| updated_at | DATETIME | NOT NULL | 수정 일시 |

### 1.1.5 견적 (quote)

| 컬럼명 | 데이터 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 견적 ID |
| quote_number | VARCHAR(100) | NOT NULL, UNIQUE | 견적 번호 |
| member_id | BIGINT | FK, NOT NULL | 작성 회원 ID |
| customer_id | BIGINT | FK, NOT NULL | 고객 ID |
| supply_amount | DECIMAL(14,2) | NOT NULL | 공급가액 |
| vat_amount | DECIMAL(14,2) | NOT NULL | 부가세 |
| total_amount | DECIMAL(14,2) | NOT NULL | 총금액 |
| status | VARCHAR(20) | NOT NULL | 견적 상태 |
| quote_date | DATE | NOT NULL | 견적 작성일 |
| created_at | DATETIME | NOT NULL | 생성 일시 |
| updated_at | DATETIME | NOT NULL | 수정 일시 |

### 1.1.6 견적 항목 (quote_item)

| 컬럼명 | 데이터 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 견적 항목 ID |
| quote_id | BIGINT | FK, NOT NULL | 견적 ID |
| product_id | BIGINT | FK, NULL 가능 | 제품 ID |
| product_name | VARCHAR(255) | NOT NULL | 견적 작성 당시 제품명 |
| product_code | VARCHAR(100) | NOT NULL | 견적 작성 당시 제품 코드 |
| sale_price | DECIMAL(12,2) | NOT NULL | 견적 작성 당시 판매 가격 |
| quantity | INT | NOT NULL | 수량 |
| amount | DECIMAL(14,2) | NOT NULL | 항목별 공급가액 |

### 1.1.7 견적 이메일 발송 이력 (quote_email_history)

| 컬럼명 | 데이터 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 발송 이력 ID |
| quote_id | BIGINT | FK, NOT NULL | 견적 ID |
| recipient_email | VARCHAR(255) | NOT NULL | 수신자 이메일 |
| subject | VARCHAR(255) | NULL | 이메일 제목 |
| status | VARCHAR(20) | NOT NULL | 발송 결과 |
| failure_reason | TEXT | NULL | 발송 실패 사유 |
| sent_at | DATETIME | NULL | 발송 성공 일시 |
| created_at | DATETIME | NOT NULL | 발송 시도 기록 일시 |

## 1.2 테이블 관계

| 테이블 | 관계 테이블 | 관계 | 설명 |
|---|---|---|---|
| member | customer | 1:N | 한 회원이 여러 고객 정보를 관리한다. |
| member | quote | 1:N | 한 회원이 여러 견적서를 작성한다. |
| category | product | 1:N | 하나의 카테고리에 여러 제품이 속할 수 있다. |
| category | category | 1:N | 하나의 상위 카테고리에 여러 하위 카테고리가 속할 수 있다. |
| customer | quote | 1:N | 한 고객에게 여러 견적서를 작성할 수 있다. |
| quote | quote_item | 1:N | 하나의 견적서에 여러 견적 항목이 포함된다. |
| product | quote_item | 1:N | 하나의 제품이 여러 견적 항목에서 사용될 수 있다. |
| quote | quote_email_history | 1:N | 하나의 견적서에 여러 이메일 발송 이력이 생성될 수 있다. |

## 1.3 주요 제약조건 및 처리 기준

| 구분 | 처리 기준 |
|---|---|
| 회원 비밀번호 | 단방향 해시 방식으로 저장한다. |
| 회원 비활성화 | 비활성화된 회원은 로그인 및 신규 견적 작성이 제한되며, 기존 데이터는 유지한다. |
| 제품 가격 | unit_price는 기준 단가, sale_price는 실제 판매 가격으로 구분한다. |
| 제품 판매 중지 | 판매 중지된 제품은 신규 견적에 추가할 수 없지만 기존 견적에서는 조회할 수 있다. |
| 제품 삭제 | 작성 중인 견적이나 저장된 견적에서 사용 중인 제품은 삭제할 수 없다. |
| 고객 비활성화 | 비활성 고객은 신규 견적에서 선택할 수 없지만 기존 견적 정보는 유지한다. |
| 견적 번호 | 자동 생성하며 UNIQUE 제약조건으로 중복을 방지한다. |
| 견적 금액 | 공급가액은 판매 가격과 수량의 곱을 합산하며, 부가세는 공급가액의 10%로 계산한다. |
| 금액 데이터 | DECIMAL 타입을 사용하고 일관된 반올림 기준을 적용한다. |
| 견적 항목 | 견적 작성 당시의 제품명, 제품 코드, 판매 가격, 수량, 금액을 저장한다. |
| 견적 상태 | DRAFT는 작성 중, SENT는 이메일 발송 성공 상태를 의미한다. |
| 이메일 발송 | 네이버 SMTP를 사용하며 견적 PDF를 첨부한다. |
| 이메일 발송 이력 | 발송 시도마다 수신자, 성공·실패 결과, 시도 일시, 실패 사유를 저장한다. |
| 이메일 재발송 | 발송 실패 이력을 보존하고 재발송할 수 있도록 한다. |
| 견적 수정 | 발송이 완료된 견적은 수정할 수 없으며 변경이 필요한 경우 새 견적을 작성한다. |
| 견적 삭제 | 견적 삭제 시 관련 견적 항목과 이메일 발송 이력도 함께 삭제한다. |
| 제품 이미지 | 프로젝트 내부 이미지 폴더에 저장하고 DB에는 이미지 경로를 저장한다. |
| 시간 관리 | 날짜와 시간은 한국 시간 기준으로 일관되게 관리한다. |
| DB 스크립트 | 테이블 생성용 schema.sql과 초기 데이터 입력용 data.sql을 분리한다. |