# QuoteOn

영업사원을 위한 견적 생성·관리 웹 애플리케이션입니다.  
BackEnd BootCamp 25기 3차 심화 프로젝트(개인) · 소지현

React + Vite 프론트엔드와 Spring Boot REST API 백엔드를 분리해, 상담 중 제품 탐색부터 견적 작성·문서 다운로드·이메일 발송까지 로컬에서 시연할 수 있도록 구성했습니다. 이 문서는 **배포가 아닌 로컬 개발·시연** 기준입니다.

관련 프론트엔드 저장소: `quoteon-web`

---

## 1. 프로젝트 소개

QuoteOn은 가구·사무용품 영업 상담 상황을 가정한 견적 시스템입니다. 영업사원은 제품 카탈로그와 고객 정보를 바탕으로 견적을 작성하고, 관리자는 회원·제품·카테고리·전체 견적을 운영합니다.

- 백엔드 저장소: API, DB 스키마, 시연 SQL, 제품 이미지, 기획·설계 문서
- 프론트엔드 저장소(`quoteon-web`): 영업사원·관리자 화면

---

## 2. 프로젝트 기획 배경 및 목적

종이·수기 견적에서 생기는 작성 시간 증가와 정보 분산을 줄이기 위해, 상담 중 제품 선택·금액 계산·견적서 전달을 한 흐름으로 묶는 것이 목표입니다.

- 제품·가격·고객·견적 내역을 시스템에서 통합 관리
- 공급가액·부가세·합계 자동 계산으로 계산 오류 감소
- 견적서 PDF·이미지 다운로드 및 실제 SMTP 이메일 발송
- 발송 이력 저장과 실패 건 재전송
- 관리자 승인·제품·카테고리·전체 견적 운영

상세 배경은 [`docs/01_planning/01_proposal.md`](docs/01_planning/01_proposal.md), 요구사항은 [`docs/01_planning/02_prd.md`](docs/01_planning/02_prd.md)를 참고합니다.

---

## 3. 주요 기능

### 회원·인증
- 영업사원/관리자 회원가입(승인 대기), 로그인·로그아웃
- 승인 대기·거절·비활성 계정 접근 제한
- 화면 우측 상단에 로그인 사용자 **이름** 표시

### 제품·카탈로그
- 카테고리·검색·페이징 기반 제품 목록
- 제품 상세(규격·설명·단가·판매가), 대표·상세 이미지 확인
- 회원별 즐겨찾기 등록·해제 및 즐겨찾기 필터

### 고객
- 고객 등록·조회·검색·수정·페이징
- 삭제 대신 비활성화(신규 견적 선택 제외, 기존 견적 유지)

### 상담·견적
- 상담 작업대에서 고객 선택, 제품 담기, 수량·금액 계산
- 견적 저장·미리보기·작성 중 취소·새 상담 확인
- 내 견적 목록·검색·상세, 발송 완료 견적 수정 제한

### 견적서·문서
- 영업사원: PDF / JPEG / PNG 다운로드
- 이메일 발송, 발송 이력 조회, 실패 건 재전송

### 관리자
- 대시보드(오늘 견적 수, 월 견적 금액, 승인 대기·활성 사용자, 인기 제품, 사용자별 견적)
- 회원 승인·거절·검색·수정·활성/비활성
- 카테고리·제품 CRUD, 이미지 업로드, 판매 중지·재개, 견적 사용 중 제품 삭제 제한
- 전체 견적 조회·검색·상세, PDF·PNG 다운로드

---

## 4. 기술 스택

| 구분 | 기술 |
| --- | --- |
| Backend | Java 25, Spring Boot (Web / Security / JDBC), Gradle |
| Frontend | React 19, TypeScript, Vite 8, Oxlint |
| Database | MySQL 9.x (`quoteon_db`) |
| 문서·메일 | PDF 생성 후 이미지 변환, 네이버 메일 SMTP(환경 변수) |
| 테스트 | JUnit(MockMvc), Node.js 내장 test runner |

확인한 로컬 예시 버전: OpenJDK Temurin 25, Node.js 24.x / npm 11.x, MySQL Community Server 9.7.

---

## 5. 시스템 아키텍처

```text
[ Browser :5173 ]
      │  /api, /images  (Vite proxy)
      ▼
[ Spring Boot :8080 ] ── JDBC ── [ MySQL quoteon_db ]
      │
      ├─ Session + CSRF 인증
      ├─ 제품 이미지 파일 (images/products)
      └─ SMTP (선택) 견적 PDF 첨부 발송
```

- 프론트엔드는 UI와 API 호출만 담당합니다.
- 백엔드는 Controller → Service → Repository(JdbcTemplate) 계층으로 REST API를 제공합니다.
- CORS는 `http://localhost:5173`, `http://127.0.0.1:5173`만 허용합니다.
- 설계 개요: [`docs/02_design/01_architecture.md`](docs/02_design/01_architecture.md)

---

## 6. 주요 구현 내용

- 회원가입 후 관리자 승인 전까지 서비스 이용 제한
- 견적 항목에 작성 시점의 제품명·코드·규격·판매가 스냅샷 저장
- 판매 중지 제품은 신규 견적에 추가 불가, 기존 견적 항목은 유지
- 견적에 쓰인 제품은 삭제 불가(사전 `usedInQuotes` 확인 및 안내 문구)
- 회원별 `product_favorite`로 즐겨찾기 분리
- 관리자 견적 상세·PDF의 공급자 영역은 와이어프레임 예시 값을 표시
- 관리자 문서 다운로드는 PDF와 PNG(`/image`)를 제공 (JPEG 관리자 API는 없음)

---

## 7. 데이터베이스 구조

주요 테이블은 `src/main/resources/schema.sql`에 정의되어 있습니다.

| 테이블 | 역할 |
| --- | --- |
| `member` | 회원 계정·권한·상태 |
| `category` | 제품 카테고리(상위·하위) |
| `product` / `product_image` | 제품·상세 이미지 |
| `product_favorite` | 회원별 제품 즐겨찾기 |
| `customer` | 영업사원별 고객 |
| `quote` / `quote_item` | 견적·견적 항목 스냅샷 |
| `quote_email_history` | 이메일 발송 이력 |

ERD·제약 설명: [`docs/02_design/02_erd.md`](docs/02_design/02_erd.md)

시연 SQL이 넣는 기본 건수(빈 `quoteon_db` 기준):

| 구분 | 개수 |
| --- | ---: |
| 회원 | 5 |
| 카테고리 | 5 |
| 제품 | 25 |
| 고객 | 12 |
| 견적 | 25 |
| 견적 항목 | 70 |
| 이메일 이력(시연용 INSERT) | 10 |
| 제품 이미지 파일 | 대표 25 + 상세 50 |

---

## 8. 실행 방법

### 8.1 사전 준비

1. Java 25, Node.js, MySQL 9.x 설치
2. 관리 계정으로 DB 생성

```text
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u 관리계정 -p -e "CREATE DATABASE quoteon_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

3. 애플리케이션 DB 계정에 `quoteon_db` 권한 부여
4. 환경 변수 설정 (아래 참고). 실제 비밀값은 `.env`에만 두고 Git에 올리지 않습니다.

### 8.2 환경 변수

| 변수 | 필수 | 설명 |
| --- | --- | --- |
| `DB_USERNAME` | 예 | MySQL 사용자 |
| `DB_PASSWORD` | 예 | MySQL 비밀번호 |
| `MAIL_HOST` | 아니오 | SMTP 호스트 |
| `MAIL_PORT` | 아니오 | SMTP 포트 |
| `MAIL_USERNAME` | 아니오 | SMTP 계정 |
| `MAIL_PASSWORD` | 아니오 | SMTP 비밀번호(앱 비밀번호 등) |
| `MAIL_PROPERTIES_MAIL_SMTP_AUTH` | 아니오 | 예: `true` |
| `MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | 아니오 | 예: `true` |

예시 파일: [`.env.example`](.env.example)  
SMTP를 비워 두면 서버는 기동되고, 메일 발송 API만 실패합니다.

### 8.3 스키마·시연 데이터

Windows PowerShell에서는 입력 리다이렉션 대신 `cmd /c`를 권장합니다.

```text
cmd /c "\"C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe\" --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u 계정 -p quoteon_db < src\main\resources\schema.sql"
cmd /c "\"C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe\" --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u 계정 -p quoteon_db < scripts\demo\a1-furniture-demo.sql"
```

- `schema.sql`: 테이블 생성
- `scripts/demo/a1-furniture-demo.sql`: 시연 데이터(이미 데이터가 있으면 중단, 덮어쓰지 않음)

### 8.4 백엔드 실행

저장소 루트에서:

```text
.\gradlew.bat bootRun
```

- API: `http://localhost:8080`
- 이미지: `http://localhost:8080/images/products/...`

### 8.5 프론트엔드 실행

`quoteon-web` 저장소에서:

```text
npm install
npm run dev
```

- 개발 서버: `http://localhost:5173` (권장)
- Vite가 `/api`, `/images`를 `http://localhost:8080`으로 프록시합니다.
- 5173이 사용 중이면 다른 포트로 올라갈 수 있으며, 그 경우 CORS로 API가 거부될 수 있습니다.

### 8.6 검사 명령

백엔드:

```text
.\gradlew.bat test
```

프론트엔드(`quoteon-web`):

```text
npx tsc --noEmit
npm run lint
node --test "src/**/*.test.ts"
```

---

## 9. 주요 API

인증·세션 쿠키와 CSRF(`XSRF-TOKEN` / `X-XSRF-TOKEN`)를 사용합니다. 관리자 API는 `ROLE_ADMIN`이 필요합니다.

| 영역 | Method | Path |
| --- | --- | --- |
| 인증 | POST | `/api/signup`, `/api/login`, `/api/logout` |
| 인증 | GET | `/api/me` |
| 카탈로그 | GET | `/api/categories`, `/api/products`, `/api/products/{id}` |
| 즐겨찾기 | POST/DELETE | `/api/products/{id}/favorite` |
| 고객 | GET/POST/PUT | `/api/customers`, `/api/customers/{id}` |
| 고객 | POST | `/api/customers/{id}/deactivate` |
| 견적 | GET/POST/PUT | `/api/quotes`, `/api/quotes/{id}` |
| 견적 문서 | GET | `/api/quotes/{id}/pdf`, `/jpeg`, `/png` |
| 견적 메일 | POST/GET | `/api/quotes/{id}/email`, `.../email/histories`, `.../retry` |
| 관리자 대시보드 | GET | `/api/admin/dashboard` |
| 관리자 회원 | GET/PUT/POST | `/api/admin/members`, `.../pending`, `.../approve|reject|activate|deactivate` |
| 관리자 카테고리 | POST/PUT/DELETE | `/api/admin/categories` |
| 관리자 제품 | GET/POST/PUT/DELETE | `/api/admin/products`, `.../stop`, `.../resume`, 이미지 업로드 |
| 관리자 견적 | GET/DELETE | `/api/admin/quotes`, `.../{id}`, `.../pdf`, `.../image` |
| 정적 이미지 | GET | `/images/products/{filename}` |

---

## 10. 시연 데이터 및 계정 안내

현재 로컬 `quoteon_db` 기준으로 시연 회원은 아래와 같습니다. 회사명은 에이원퍼니처, 상태는 모두 `ACTIVE`입니다.

| 이메일 | 이름 | 권한 |
| --- | --- | --- |
| `admin1@aonef.com` | 김관리 | 관리자 |
| `sales1@aonef.com` | 김영업 | 영업사원 |
| `sales2@aonef.com` | 박서연 | 영업사원 |
| `sales3@aonef.com` | 이준호 | 영업사원 |
| `sales4@aonef.com` | 최민아 | 영업사원 |

- 로컬 시연용 임시 비밀번호: `1234` (운영 환경에서 사용하지 마세요)
- SQL 파일에는 BCrypt 해시만 저장되어 있습니다.
- 제품 이미지: `images/products`의 `A1-*.jpg` (대표 25 + 상세 50)
- 시연 이메일 이력 10건은 SMTP 실발송 기록이 아니라 SQL로 넣은 화면 시연용 데이터입니다.

---

## 11. 프로젝트 문서

| 문서 | 경로 |
| --- | --- |
| 기획서 | [`docs/01_planning/01_proposal.md`](docs/01_planning/01_proposal.md) |
| 요구사항 명세서 | [`docs/01_planning/02_prd.md`](docs/01_planning/02_prd.md) |
| 아키텍처 | [`docs/02_design/01_architecture.md`](docs/02_design/01_architecture.md) |
| ERD | [`docs/02_design/02_erd.md`](docs/02_design/02_erd.md) |
| UI 와이어프레임 | [`docs/02_design/03_ui_wireframe.md`](docs/02_design/03_ui_wireframe.md) |
| 트러블슈팅 | [`docs/03_reports/quoteon-troubleshooting.md`](docs/03_reports/quoteon-troubleshooting.md) |

---

## 12. 개발자 정보

- 이름: 소지현
- 과정: BackEnd BootCamp 25기 3차 심화 프로젝트(개인)
- 역할: 기획·백엔드·프론트엔드 구현 및 로컬 시연 환경 구성
