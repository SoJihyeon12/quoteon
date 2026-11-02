# quoteon

BackEnd BootCamp 25기 3차 심화 프로젝트 - (개인) 소지현

이 문서는 팀원이 로컬에서 같은 에이원퍼니처 시연 환경을 구성하는 순서다. 배포 절차는 포함하지 않는다. 시연 비밀번호는 로컬 시연 전용이며 운영 환경에서 사용하지 않는다.

## 환경 준비

이 저장소에서 확인한 버전은 다음과 같다.

- Java 25. `build.gradle`의 toolchain은 Java 25이고, 확인한 실행 환경은 OpenJDK Temurin 25.0.3이다.
- Node.js 24.18.0, npm 11.16.0. 프론트엔드 `package.json`은 Node 버전을 고정하지 않는다.
- MySQL Community Server 9.7. 백엔드 포트는 `application.yaml`의 8080이다.

백엔드 데이터베이스 이름은 `quoteon_db`다. 주소는 `application.yaml`에 `jdbc:mysql://localhost:3306/quoteon_db`로 들어 있다. 애플리케이션 계정에는 데이터베이스를 만드는 권한이 없다. 데이터베이스 생성은 그 권한이 있는 MySQL 관리 계정으로 한다.

```text
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u 관리계정 -p -e "CREATE DATABASE quoteon_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

생성 뒤에는 애플리케이션이 사용할 계정에 `quoteon_db` 권한을 부여한다. 백엔드는 다음 환경 변수로 접속한다.

- `DB_USERNAME`
- `DB_PASSWORD`

비밀번호와 계정은 `.env`에 두더라도 Git에 올리지 않는다. `.gitignore`는 `.env`와 `.env.*`를 무시한다. `.env.example`만 예외다.

SMTP는 환경 변수가 없어도 애플리케이션이 시작된다. 값이 없으면 메일 발송 요청만 실패한다. `application.yaml`이 읽는 변수는 다음과 같다.

- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_PROPERTIES_MAIL_SMTP_AUTH`
- `MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`

## DB 구성

자동 테스트용 `data.sql`, `test-reset.sql`은 시연 데이터와 별도다. 시연 SQL은 애플리케이션 시작 시 실행되지 않는다. `quoteon_db`를 비우거나 초기화하는 명령은 제공하지 않는다.

백엔드 저장소 루트에서 MySQL 클라이언트로 실행한다. 이 환경의 클라이언트는 `C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe`다. PowerShell에서는 입력 리다이렉션 대신 `cmd /c`를 사용한다.

```text
cmd /c "\"C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe\" --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u 계정 -p quoteon_db < src\main\resources\schema.sql"
cmd /c "\"C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe\" --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u 계정 -p quoteon_db < scripts\demo\a1-furniture-demo.sql"
```

`schema.sql`은 테이블만 만든다. `scripts/demo/a1-furniture-demo.sql`이 빈 `quoteon_db`에 시연 계정과 데이터를 넣는다. 애플리케이션 코드는 회원 ID를 고정하지 않으므로, 새 데이터베이스의 회원 번호는 자동 증가 값이다.

생성되는 데이터는 다음과 같다.

| 구분 | 개수 |
| --- | ---: |
| 회원 | 5명 |
| 카테고리 | 5개 |
| 제품 | 25개 |
| 거래처 | 12개 |
| 견적 | 25건 |
| 견적 상세 | 70건 |
| 견적 이메일 이력 | 10건 |
| 대표 이미지 경로 | 25건 |
| 상세 이미지 경로 | 50건 |

이메일 이력 10건은 SMTP로 보낸 기록이 아니다. 화면 시연용으로 SQL에서 직접 넣은 값이다.

시연 SQL은 다음 경우 데이터를 넣기 전에 중단한다.

- 접속한 데이터베이스 이름이 `quoteon_db`가 아닐 때. 오류 테이블 이름은 `quoteon_demo_refused_wrong_database`다.
- `member`, `category`, `product`, `product_image`, `customer`, `quote`, `quote_item`, `quote_email_history` 중 하나라도 행이 있을 때. 오류 테이블 이름은 `quoteon_demo_refused_precondition`다.
- 삽입 후 개수와 이미지 경로 검증이 실패할 때. 오류 테이블 이름은 `quoteon_demo_refused_validation`이고, 삽입은 커밋되지 않는다.

이미 시연 데이터가 있는 데이터베이스에서 다시 실행하면 기존 행을 지우거나 덮어쓰지 않고 중단된다. 테스트 데이터베이스 `quote_db`에서도 이름이 달라 중단된다.

## 시연 계정

다섯 계정 모두 회사명은 에이원퍼니처이고 상태는 활성이다. 시연용 임시 비밀번호는 `1234`다. SQL 파일에는 BCrypt 해시만 있고 평문 비밀번호는 없다. 이 비밀번호는 로컬 시연용이며 운영 환경에서 사용하지 않는다.

| 이메일 | 이름 | 권한 |
| --- | --- | --- |
| `admin1@aonef.com` | 김관리 | `ROLE_ADMIN` |
| `sales1@aonef.com` | 김영업 | `ROLE_USER` |
| `sales2@aonef.com` | 박서연 | `ROLE_USER` |
| `sales3@aonef.com` | 이준호 | `ROLE_USER` |
| `sales4@aonef.com` | 최민아 | `ROLE_USER` |

공유 스크립트의 관리자와 `sales1@aonef.com` 전화번호는 비워 둔다. 로컬에 이미 있는 전화번호는 저장소로 복사하지 않는다.

## 이미지

제품 사진은 백엔드 저장소의 `images/products`에 있다. 대표 이미지는 `A1-{상품코드}.jpg`, 상세 이미지는 `A1-{상품코드}-d1.jpg`와 `A1-{상품코드}-d2.jpg`다. 대표 25장과 상세 50장, 총 75장이다.

DB에 저장하는 경로는 `/images/products/{파일명}`이다. 애플리케이션은 `GET /images/products/{파일명}`으로 JPEG를 제공한다. `.gitignore`는 `images/products`의 다른 업로드 파일을 무시하고 `A1-*.jpg`만 추적한다.

클론 뒤에 사진이 보이지 않으면 다음을 확인한다.

- `images/products`에 `A1-*.jpg`가 75개 있는지
- DB의 `product.image_path`와 `product_image.image_path`가 위 파일명과 같은지
- `http://localhost:8080/images/products/A1-DSK-1400.jpg` 요청이 `image/jpeg`를 반환하는지

상담 상품 목록과 제품 상세 팝업은 이미지 경로가 없으면 `이미지 없음`을 표시한다.

## 애플리케이션 실행

처음 실행하는 순서는 다음과 같다.

1. Java 25, Node.js, MySQL 9.7을 준비한다.
2. 관리 계정으로 `quoteon_db`를 만든다.
3. `DB_USERNAME`과 `DB_PASSWORD`를 설정한다. SMTP 변수는 필요할 때만 설정한다.
4. `schema.sql`을 실행한다.
5. `scripts/demo/a1-furniture-demo.sql`을 실행한다.
6. 백엔드 저장소에서 `.\gradlew.bat bootRun`을 실행한다. 접속 주소는 `http://localhost:8080`이다.
7. 프론트엔드 저장소 `quoteon-web`에서 `npm install` 후 `npm run dev`를 실행한다.
8. 브라우저에서 `http://localhost:5173`으로 접속한다.

백엔드 CORS는 `http://localhost:5173`과 `http://127.0.0.1:5173`만 허용한다. Vite 설정은 포트를 지정하지 않으며, 5173이 이미 사용 중이면 다음 포트로 올라간다. 그 주소에서는 브라우저의 API 요청이 거부된다. 프론트엔드 개발 서버는 `/api`와 `/images`를 `http://localhost:8080`으로 프록시한다.

프론트엔드 저장소는 별도로 관리한다. 검사 명령은 `npx tsc -b`와 `npm run lint`다.
