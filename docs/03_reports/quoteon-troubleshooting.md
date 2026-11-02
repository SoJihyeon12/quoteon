# QuoteOn 트러블슈팅

이 문서는 QuoteOn 백엔드의 현재 소스와 Git 이력을 기준으로 정리했다. 확인되지 않은 장애 수치나 이 저장소에 없는 기능은 넣지 않았다.

`docs/03_reports/troubleshooting.md`는 게시글과 댓글을 다룬 별도 예제다. QuoteOn의 회원, 제품, 견적 구현 기록이 아니므로 수정하지 않았다.

기준 커밋은 `main`의 `b634124`다. 관리자 견적 검색, 견적 삭제, 판매 재개, 메일 미설정 테스트는 그 커밋 이후 작업 트리에 있다. 해당 항목은 아래 본문에서 따로 표시했다.

## 목차

- [TS-QO-01: 테스트가 개발 데이터베이스를 건드리지 않도록 분리](#ts-qo-01-테스트가-개발-데이터베이스를-건드리지-않도록-분리)
- [TS-QO-02: SMTP 환경 변수가 있을 때 메일 미설정 테스트가 실패](#ts-qo-02-smtp-환경-변수가-있을-때-메일-미설정-테스트가-실패)
- [TS-QO-03: 발송 완료 견적이 수정되는 문제](#ts-qo-03-발송-완료-견적이-수정되는-문제)
- [TS-QO-04: 견적 삭제와 제품 삭제의 외래 키 처리가 다름](#ts-qo-04-견적-삭제와-제품-삭제의-외래-키-처리가-다름)
- [TS-QO-05: 견적 목록의 품목 검색이 행을 늘리지 않도록 조회](#ts-qo-05-견적-목록의-품목-검색이-행을-늘리지-않도록-조회)
- [TS-QO-06: 제품 목록을 행마다 다시 조회하지 않음](#ts-qo-06-제품-목록을-행마다-다시-조회하지-않음)
- [TS-QO-07: 판매 중지와 판매 재개의 권한 및 상태 전이](#ts-qo-07-판매-중지와-판매-재개의-권한-및-상태-전이)
- [TS-QO-08: 시연 SQL이 다른 데이터베이스와 기존 데이터를 보호](#ts-qo-08-시연-sql이-다른-데이터베이스와-기존-데이터를-보호)
- [TS-QO-09: 같은 시각에 만든 견적 번호가 충돌](#ts-qo-09-같은-시각에-만든-견적-번호가-충돌)

## TS-QO-01: 테스트가 개발 데이터베이스를 건드리지 않도록 분리

### 1. 문제 상황

`application.yaml`의 데이터소스는 `quoteon_db`다. `spring.sql.init`가 없어서 MySQL에서는 `schema.sql`과 `data.sql`이 애플리케이션 기동 때 실행되지 않는다. 테스트를 이 설정 그대로 두면 개발용 데이터베이스를 읽거나, 초기화 모드를 켜는 순간 개발 데이터를 지울 수 있다.

### 2. 발생 원인

Spring Boot의 SQL 초기화 기본 모드는 내장 데이터베이스용이다. 외부 MySQL은 그 기본값으로 스키마를 다시 만들지 않는다. 그래서 테스트와 개발 서버가 같은 JDBC URL을 쓰면 검증 데이터가 개발 데이터와 섞인다. 반대로 초기화를 무조건 켜면 `quoteon_db`의 회원과 견적까지 테스트 리셋 대상이 된다.

### 3. 해결 과정

테스트 프로필의 URL을 `quote_db`로 고정하고, 초기화 순서를 `test-reset.sql`, `schema.sql`, `data.sql`로 두었다. 그 앞에 `TestDatabaseCreator`를 두어 초기화 모드가 `always`일 때만 동작하게 했다. URL에 `quote_db`가 없거나 `quoteon_db`가 들어 있으면 데이터베이스를 만들기 전에 예외로 중단한다.

```java
if (!url.contains("/" + TEST_DATABASE)) {
    throw new IllegalStateException("테스트 스키마 초기화는 " + TEST_DATABASE + "에서만 실행할 수 있습니다.");
}
if (url.contains("/" + APPLICATION_DATABASE)) {
    throw new IllegalStateException("테스트 스키마 초기화는 " + APPLICATION_DATABASE + "에서 실행할 수 없습니다.");
}
```

### 4. 수정한 코드 및 파일

- `src/test/resources/application-test.yaml`
- `src/test/resources/test-reset.sql`
- `src/test/java/net/likelion/bebc25/quoteon/support/TestDatabaseCreator.java`
- `src/test/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`

`TestDatabaseCreator`는 `ee443d3`에 포함되어 있다.

### 5. 해결 결과

테스트 초기화는 `quote_db`에서만 진행된다. 개발용 `quoteon_db` URL로 초기화가 켜지면 테이블을 지우기 전에 중단된다.

### 6. 배운 점 및 개선 사항

테스트 전용 데이터베이스 이름만으로는 부족하다. 초기화 코드가 개발 URL을 거부해야 실수로 운영에 가까운 로컬 데이터를 비우지 않는다.

## TS-QO-02: SMTP 환경 변수가 있을 때 메일 미설정 테스트가 실패

### 1. 문제 상황

로컬에서 `./gradlew test`를 실행하면 `QuoteEmailMissingConfigurationTest.missingMailSettingsFailBeforeAnyExternalSend`가 실패했다. 같은 실행에서 나머지 테스트는 통과했고, 실패는 146개 중 1개였다. 이 테스트와 `QuoteMailProperties`는 아직 `main`에 커밋되지 않은 작업 트리다.

### 2. 발생 원인

테스트는 메일 설정이 없을 때 HTTP 500과 `MAIL_NOT_CONFIGURED`를 기대한다. 메시지는 `QuoteMailProperties.missing()`이 만드는 누락 변수 이름이다. 로컬 프로세스에는 `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_PROPERTIES_MAIL_SMTP_AUTH`, `MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`이 설정되어 있었다. 설정이 있으면 이 경로는 미설정 오류를 던지지 않는다.

```java
List<String> missing = quoteMailProperties.missing();
if (!missing.isEmpty()) {
    recordFailure(quoteId, recipient, subject, "메일 발송 설정이 없습니다: " + String.join(", ", missing));
    throw new QuoteMailNotConfiguredException(missing);
}
```

### 3. 해결 과정

제품 코드를 바꾸지 않았다. 여섯 환경 변수만 비운 자식 프로세스에서 해당 테스트만 다시 실행했다. 부모 프로세스의 환경 변수는 유지했다.

### 4. 수정한 코드 및 파일

코드 변경은 없다. 확인에 사용한 클래스는 다음이다.

- `src/test/java/net/likelion/bebc25/quoteon/QuoteEmailMissingConfigurationTest.java`
- `src/main/java/net/likelion/bebc25/quoteon/quote/QuoteMailProperties.java`
- `src/main/java/net/likelion/bebc25/quoteon/quote/QuoteEmailService.java`

### 5. 해결 결과

환경 변수를 제거한 재실행은 성공했다. 실패 원인은 메일 전송 구현의 회귀가 아니라, 미설정 상태를 가정한 테스트와 로컬 SMTP 설정이 동시에 존재한 것이다.

### 6. 배운 점 및 개선 사항

설정 부재를 검증하는 테스트는 개발자 머신의 환경 변수에 영향을 받는다. 테스트 프로세스 안에서 해당 속성을 비우는 편이 로컬 설정과 충돌하지 않는다. 그 변경은 이 문서 작성 시점에 아직 반영하지 않았다.

## TS-QO-03: 발송 완료 견적이 수정되는 문제

### 1. 문제 상황

견적 수정 API가 상태와 관계없이 고객, 품목, 금액을 다시 쓰면 이미 발송한 견적의 공급가액과 품목이 바뀐다. 발송 이력과 견적 내용이 어긋난다.

### 2. 발생 원인

수정 경로는 견적 소유자만 확인하고 바로 `updateQuote`와 품목 교체를 수행했다. `quote.status`가 `SENT`여도 같은 경로를 탔다.

### 3. 해결 과정

소유자 확인 다음에 상태가 `DRAFT`인지 검사한다. 그 외 상태면 `QuoteNotEditableException`을 던지고 금액과 품목을 갱신하지 않는다. 커밋 `caf0c0f`의 제목은 `feat: 발송 완료 견적 수정 제한`이다.

```java
if (!QuoteRepository.DRAFT.equals(access.quote().status())) {
    throw new QuoteNotEditableException();
}
```

### 4. 수정한 코드 및 파일

- `src/main/java/net/likelion/bebc25/quoteon/quote/QuoteService.java`
- `src/main/java/net/likelion/bebc25/quoteon/quote/QuoteNotEditableException.java`
- `src/main/java/net/likelion/bebc25/quoteon/common/ApiExceptionHandler.java`
- `src/test/java/net/likelion/bebc25/quoteon/QuoteApiTest.java`

### 5. 해결 결과

`QuoteApiTest.rejectsUpdateOfSentQuoteWithoutChangingAmountsOrItems`는 상태를 `SENT`로 바꾼 뒤 수정을 요청하고, 응답 메시지가 `발송 완료된 견적은 수정할 수 없습니다.`이며 저장된 상태가 `SENT`로 남는지를 확인한다.

### 6. 배운 점 및 개선 사항

상태 제약은 화면에서만 숨기면 같은 API를 직접 호출할 때 풀린다. 저장 전에 현재 상태를 읽고, 갱신 건수가 1이 아니면 실패시키는 검사와 함께 두는 편이 안전하다.

## TS-QO-04: 견적 삭제와 제품 삭제의 외래 키 처리가 다름

### 1. 문제 상황

부모 행을 지울 때 자식 외래 키가 남으면 MySQL이 삭제를 거부한다. 견적에는 품목과 메일 이력이 있고, 제품에는 상세 이미지와 견적 품목 참조가 있다. 두 삭제를 같은 방식으로 처리하면 한쪽은 실패하고 한쪽은 참조 무결성이 깨질 수 있다.

### 2. 발생 원인

`schema.sql`에서 `quote_item`와 `quote_email_history`는 `quote`를 `ON DELETE CASCADE`로 참조한다. `product_image`는 `product`를 참조하지만 cascade가 없다. `quote_item.product_id`도 제품을 참조하며 cascade가 없다. 따라서 견적 삭제는 부모 한 번으로 자식이 따라가고, 제품 삭제는 상세 이미지를 먼저 지워야 하며 견적에 쓰인 제품은 지우면 안 된다.

### 3. 해결 과정

관리자 견적 삭제는 `DELETE FROM quote WHERE id = ?` 한 문장이다. 품목과 메일 이력은 스키마의 cascade에 맡긴다. 반환된 갱신 건수가 0이면 견적이 없는 것으로 처리한다.

제품 삭제는 트랜잭션 안에서 견적 품목 참조를 먼저 센다. 1건 이상이면 `ProductInUseException`이다. 없을 때만 `product_image`를 지우고 이어서 제품을 지운다. 그 사이에 참조가 생기면 `DataIntegrityViolationException`을 같은 사용 중 오류로 바꾼다.

판매 중지와 삭제가 다르다. 중지된 제품의 기존 견적 품목은 남긴다. `AdminProductSaleApiTest.stopSaleBlocksNewQuotesAndKeepsExistingQuoteItems`가 이 차이를 검증한다.

### 4. 수정한 코드 및 파일

커밋된 스키마와 제품 삭제 흐름:

- `src/main/resources/schema.sql`
- `src/main/java/net/likelion/bebc25/quoteon/product/ProductService.java`
- `src/test/java/net/likelion/bebc25/quoteon/AdminProductSaleApiTest.java`

작업 트리의 견적 삭제:

- `src/main/java/net/likelion/bebc25/quoteon/quote/AdminQuoteRepository.java`
- `src/main/java/net/likelion/bebc25/quoteon/quote/AdminQuoteService.java`

### 5. 해결 결과

견적 삭제는 자식 이력을 애플리케이션이 하나씩 지울 필요가 없다. 제품 삭제는 상세 이미지 때문에 부모를 먼저 지우지 않고, 견적에서 참조 중인 제품은 삭제 API가 거부한다.

### 6. 배운 점 및 개선 사항

cascade는 자식의 생명주기가 부모와 완전히 같을 때만 쓴다. 견적 품목에 복사된 제품명과 가격은 제품이 중지되거나 삭제되지 않아도 보존되어야 하므로 제품 외래 키에는 cascade를 넣지 않았다.

## TS-QO-05: 견적 목록의 품목 검색이 행을 늘리지 않도록 조회

### 1. 문제 상황

관리자 견적 목록에서 제품명이나 제품 코드로 검색하려면 `quote_item`이 필요하다. 목록 쿼리에 품목을 그대로 조인하면 품목이 여러 개인 견적이 목록과 `COUNT(*)`에서 여러 행으로 계산된다. 이 구현은 작업 트리에 있으며 `b634124`에는 없다.

### 2. 발생 원인

견적과 품목은 1:N이다. 조인 결과의 행 수는 견적 수가 아니라 조건에 맞는 품목 수다. 페이지의 `LIMIT`도 견적 기준이 아니라 조인된 행 기준이 된다.

### 3. 해결 과정

목록과 건수 쿼리는 견적, 작성자, 고객만 조인한다. 품목 조건은 `EXISTS`로 확인해 견적당 최대 한 행을 유지한다. 상세 화면의 품목은 목록 쿼리에 붙이지 않고, 견적 하나를 연 뒤 `quote_id`로 한 번 더 조회한다.

```sql
AND (
      ? = 0
      OR EXISTS (
          SELECT 1
          FROM quote_item qi
          WHERE qi.quote_id = q.id
            AND (
                  qi.product_name LIKE ? ESCAPE '\\'
                  OR qi.product_code LIKE ? ESCAPE '\\'
            )
      )
)
```

이 저장소에는 게시글, 좋아요, 스크랩 테이블이 없다. 게시물 20개에 대해 좋아요와 스크랩을 각각 조회해 추가 쿼리가 40개 발생했다는 이력도 없다. 따라서 `1 + 2N`을 단일 쿼리로 줄인 변경으로 기록할 수 없다.

### 4. 수정한 코드 및 파일

- `src/main/java/net/likelion/bebc25/quoteon/quote/AdminQuoteRepository.java`
- `src/main/java/net/likelion/bebc25/quoteon/quote/AdminQuoteService.java`

### 5. 해결 결과

품목 검색이 켜져도 목록의 한 행은 견적 하나다. 상세 품목은 열린 견적당 쿼리 1회다. 조인 방식과 비교한 실행 계획 수치는 측정하지 않았다.

### 6. 배운 점 및 개선 사항

1:N을 목록에 보여줄 필요가 없으면 조인보다 `EXISTS`가 건수와 페이지를 단순하게 유지한다. 목록 화면에 품목 행 자체가 필요해지면 이 쿼리를 다시 설계해야 한다.

## TS-QO-06: 제품 목록을 행마다 다시 조회하지 않음

### 1. 문제 상황

상담 제품 목록은 카테고리 이름, 판매가, 대표 이미지가 필요하다. 제품마다 카테고리와 이미지를 따로 조회하면 페이지 크기만큼 쿼리가 늘어난다. 상세 이미지 1·2·3번까지 목록에서 읽으면 증가 폭은 더 커진다.

### 2. 발생 원인

대표 이미지는 `product.image_path`에 있고 상세 이미지는 `product_image`에 있다. 목록 DTO는 대표 경로만 필요하다. 상세 이미지까지 목록 루프에서 읽으면 목록 API가 상세 API의 일을 반복한다.

### 3. 해결 과정

판매 중 목록은 `product`와 `category`를 한 번 조인하고 `status`, 키워드, 카테고리, `LIMIT`, `OFFSET`을 같은 문장에서 처리한다. 건수는 같은 조건의 `COUNT(*)`다. 키워드의 `%`와 `_`는 `toContainsPattern`에서 이스케이프한다.

상세 API만 `product_image`를 제품 ID로 한 번 조회해 `detailImages`에 넣는다. `ProductService.getProducts`에는 제품별 이미지 조회 루프가 없다.

### 4. 수정한 코드 및 파일

- `src/main/java/net/likelion/bebc25/quoteon/product/ProductRepository.java`
- `src/main/java/net/likelion/bebc25/quoteon/product/ProductService.java`
- `src/main/java/net/likelion/bebc25/quoteon/product/ProductImageRepository.java`

### 5. 해결 결과

판매 목록 한 페이지는 건수 쿼리와 목록 쿼리다. 카테고리 이름은 조인으로 함께 온다. 상세 이미지 쿼리는 상세 요청에만 있다. 페이지 크기 20에 대한 쿼리 실행 횟수는 프로파일러로 측정하지 않았다.

### 6. 배운 점 및 개선 사항

목록과 상세가 필요로 하는 이미지 범위가 다르면 저장 위치도 나누는 편이 낫다. 대표 이미지는 제품 행에 두고, 슬롯이 있는 상세 이미지는 별도 테이블에 두어 목록 쿼리가 슬롯 수에 비례하지 않게 했다.

## TS-QO-07: 판매 중지와 판매 재개의 권한 및 상태 전이

### 1. 문제 상황

판매 중지와 재개를 일반 수정 API로 처리하면 영업사원도 상태를 바꿀 수 있고, 이미 중지된 제품을 다시 중지하거나 판매 중인 제품을 재개하는 요청이 성공처럼 보일 수 있다.

### 2. 발생 원인

제품 관리 API는 `/api/admin/products` 아래에 있다. `SecurityConfig`는 이 경로를 `ROLE_ADMIN`에만 허용한다. 상태 변경은 현재 상태가 기대값일 때만 갱신해야 한다. 조건 없는 `UPDATE`는 이미 같은 상태여도 성공 건수를 만든다.

### 3. 해결 과정

중지는 `ACTIVE`일 때만 `INACTIVE`로, 재개는 `INACTIVE`일 때만 `ACTIVE`로 바꾼다. 갱신 건수가 1이 아니면 제품 존재 여부를 구분해 없거나 상태 충돌로 응답한다.

```java
public int updateStatusIfCurrent(long id, String status, String expectedStatus) {
    return jdbcTemplate.update(
            "UPDATE product SET status = ? WHERE id = ? AND status = ?",
            status,
            id,
            expectedStatus
    );
}
```

판매 중지는 `ee443d3`의 `ProductService.stopSale`에 있다. `resumeSale`은 현재 작업 트리에 있다. 테스트 `stopAndDeleteRequireAdminAndCsrf`는 관리자 세션과 CSRF가 없는 요청을 거부하는지 본다.

### 4. 수정한 코드 및 파일

- `src/main/java/net/likelion/bebc25/quoteon/security/SecurityConfig.java`
- `src/main/java/net/likelion/bebc25/quoteon/product/AdminProductController.java`
- `src/main/java/net/likelion/bebc25/quoteon/product/ProductRepository.java`
- `src/main/java/net/likelion/bebc25/quoteon/product/ProductService.java`
- `src/test/java/net/likelion/bebc25/quoteon/AdminProductSaleApiTest.java`

### 5. 해결 결과

영업사원 세션은 관리자 제품 경로에 접근하지 못한다. 이미 중지된 제품의 중지 요청과 판매 중인 제품의 재개 요청은 상태 충돌이다. 중지 후에도 기존 견적 품목의 이름, 코드, 가격은 유지된다.

### 6. 배운 점 및 개선 사항

권한은 경로 단위로 막고, 상태 전이는 쿼리의 `WHERE`로 한 번 더 막는다. 두 검사는 대체 관계가 아니다.

## TS-QO-08: 시연 SQL이 다른 데이터베이스와 기존 데이터를 보호

### 1. 문제 상황

에이원퍼니처 시연 데이터는 회원, 제품, 견적, 이미지 경로를 한 번에 넣는다. 테스트용 `quote_db`나 이미 데이터가 있는 `quoteon_db`에서 실행되면 개발 데이터가 중복되거나 덮어써질 수 있다. 애플리케이션 코드는 회원 ID 51, 52를 참조하지 않는데, 초기 시연 SQL은 그 번호가 미리 있어야 동작했다.

### 2. 발생 원인

`application.yaml`의 데이터베이스 이름은 `quoteon_db`로 고정되어 있다. 시연 SQL을 애플리케이션 시작 SQL에 넣으면 매 기동마다 실행된다. 또한 회원 번호를 환경마다 고정하면 빈 데이터베이스에서는 삽입 전에 실패한다.

### 3. 해결 과정

시연 SQL은 `scripts/demo/a1-furniture-demo.sql`에만 둔다. `schema.sql`, `data.sql`, `test-reset.sql`과 분리되어 있고 기동 시 실행되지 않는다. 데이터베이스 이름이 `quoteon_db`가 아니면 `quoteon_demo_refused_wrong_database` 조회를 시도해 중단한다. 회원, 카테고리, 제품, 이미지, 고객, 견적, 품목, 메일 이력 중 하나라도 행이 있으면 `quoteon_demo_refused_precondition`으로 중단한다. 삽입 후 개수와 이미지 경로 검증이 실패하면 `quoteon_demo_refused_validation`으로 커밋 전에 중단한다.

빈 `quoteon_db`에서는 관리자와 영업사원 5명을 이메일 기준으로 만들고, 견적과 고객은 회원 ID 상수가 아니라 이메일 조인으로 연결한다. 이 파일은 `b634124`에 들어 있다.

별도 MySQL에서 확인한 결과는 다음과 같다.

| 확인 | 결과 |
| --- | --- |
| 빈 `quoteon_db`에서 첫 실행 | 회원 5, 카테고리 5, 제품 25, 고객 12, 견적 25, 품목 70, 메일 이력 10, 대표 경로 25, 상세 경로 50 |
| 같은 데이터베이스에서 재실행 | 삽입 전 중단, 건수 유지 |
| 검증을 실패시킨 실행 | 커밋되지 않아 회원·제품·견적 0 |
| 이름이 다른 데이터베이스 | 삽입 전 중단 |
| 기존 `quoteon_db` | 실행 전후 건수 동일 |

### 4. 수정한 코드 및 파일

- `scripts/demo/a1-furniture-demo.sql`
- `README.md`

### 5. 해결 결과

시연 SQL은 빈 `quoteon_db`에서 한 번 구성된다. 대상이 다르거나 이미 데이터가 있으면 기존 행을 삭제하지 않고 멈춘다. 이미지 경로는 `/images/products/{파일명}`이며 대표 25장과 상세 50장 파일과 맞춘다.

### 6. 배운 점 및 개선 사항

반복 실행을 막을 때 `DELETE` 후 재삽입을 제공하지 않았다. 로컬에 이미 견적과 회원이 있는 팀원이 스크립트를 다시 실행해도 그 데이터를 비우면 안 되기 때문이다.

## TS-QO-09: 같은 시각에 만든 견적 번호가 충돌

### 1. 문제 상황

`quote.quote_number`는 유일하다. 번호를 날짜와 시퀀스만으로 만들면 같은 날짜에 동시에 저장한 요청이 같은 번호를 받아 `DuplicateKeyException`으로 실패할 수 있다.

### 2. 발생 원인

견적 번호는 사람이 읽는 식별자이면서 유니크 제약을 가진다. 날짜만 넣으면 하루의 첫 견적부터 충돌 가능성이 생기고, 데이터베이스 자동 증가 ID를 그대로 보여 주면 번호 형식이 요구와 달라진다.

### 3. 해결 과정

`QuoteNumbers.next`는 `Q-`와 기본 ISO 날짜, 8바이트 난수를 16진수로 붙인다. `QuoteService.insertQuote`는 고유 키 충돌이 나면 최대 5번 새 번호를 만든다. 5번이 모두 실패하면 마지막 예외를 전달한다.

### 4. 수정한 코드 및 파일

- `src/main/java/net/likelion/bebc25/quoteon/quote/QuoteNumbers.java`
- `src/main/java/net/likelion/bebc25/quoteon/quote/QuoteService.java`
- `src/main/resources/schema.sql`의 `quote_number` 유니크 제약

### 5. 해결 결과

정상적인 저장은 첫 번호로 끝난다. 충돌은 예외를 삼키지 않고 재시도 횟수 안에서만 흡수한다. 실제 충돌이 며칠에 몇 번 발생했는지는 측정하지 않았다.

### 6. 배운 점 및 개선 사항

난수는 충돌 확률을 낮출 뿐 제약을 대신하지 않는다. 유니크 제약을 남기고, 애플리케이션은 그 제약 위반만 제한된 횟수만큼 다시 시도하는 구조가 맞다.
