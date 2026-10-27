# 1. 트러블슈팅 및 기술 문제 해결 기록

## 목차
- [1. 트러블슈팅 및 기술 문제 해결 기록](#1-트러블슈팅-및-기술-문제-해결-기록)
- [TS-YONG-01: HandlerInterceptor에서 세션 검증 후 리다이렉트 무한 루프 발생 문제](#ts-yong-01-handlerinterceptor에서-세션-검증-후-리다이렉트-무한-루프-발생-문제)
- [TS-HARU-01: JdbcTemplate post와 reply 조인 쿼리 실행 시 1:N 댓글 중복 조회 문제](#ts-haru-01-jdbctemplate-post와-reply-조인-쿼리-실행-시-1n-댓글-중복-조회-문제)

---

## TS-YONG-01: HandlerInterceptor에서 세션 검증 후 리다이렉트 무한 루프 발생 문제

### 1) 발생 현상 및 에러
미인증 사용자의 글 작성 접근을 막기 위해 HandlerInterceptor를 적용했으나, 로그인 페이지(`/members/login`) 및 정적 리소스(CSS/JS) 요청 시에도 인터셉터가 동작하여 `Too Many Redirects` 에러 발생.

### 2) 원인 분석
WebMvcConfigurer 인터페이스 구현체의 `addInterceptors()` 설정 메서드에서 로그인 폼 요청 URL(`/members/login`), 회원가입 URL(`/members/join`), static 리소스 경로(`/css/**`, `/js/**`)에 대한 `excludePathPatterns()` 제외 설정 누락 확인.

### 3) 해결 방법
WebConfig 클래스 내 인터셉터 등록 시 제외 경로를 명확하게 지정.

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginCheckInterceptor())
                .order(1)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/", "/members/join", "/members/login", "/members/logout",
                        "/posts", "/posts/{id}", "/css/**", "/js/**", "/favicon.ico"
                );
    }
}
```

### 4) 시사점 및 배운 점
인터셉터 적용 시 전역 경로(`/**`)를 대상으로 설정할 때는 로그인/회원가입 처리 요청뿐만 아니라 static 정적 자원 및 비회원 허용 페이지의 제외 경로(`excludePathPatterns`)를 사전에 정밀하게 설계해야 런타임 무한 리다이렉트 방지 가능.

---

## TS-HARU-01: JdbcTemplate post와 reply 조인 쿼리 실행 시 1:N 댓글 중복 조회 문제

### 1) 발생 현상 및 에러
게시글 상세 보기 기능 구현 중, 게시글(post) 정보와 해당 글에 달린 댓글(reply) 목록을 한 번의 1:N 조인 SQL로 가져오기 위해 Query를 실행했으나, 댓글 개수만큼 게시글 객체 및 댓글 데이터가 중복 렌더링되는 결과 발생.

### 2) 원인 분석
1:N 조인 쿼리 실행 결과 ResultSet 행(Row) 수가 댓글 개수만큼 늘어나게 되어, 일반 RowMapper를 단순 반복 사용할 경우 동일한 게시글 엔티티가 댓글 수만큼 반복 생성되는 ResultSet 매핑 특성 원인.

### 3) 해결 방법
ResultSetExtractor 인터페이스를 구현하여 게시글 PK(`post_id`) 기준으로 맵(Map) 객체에 1개의 게시글만 생성/저장하고, 조인되어 돌아오는 댓글 행들은 동일한 게시글의 댓글 List에 추가하는 방식으로 매핑 로직 수정.

```java
public class PostWithRepliesExtractor implements ResultSetExtractor<Post> {

    @Override
    public Post extractData(ResultSet rs) throws SQLException, DataAccessException {
        Post post = null;
        while (rs.next()) {
            if (post == null) {
                post = new Post();
                post.setId(rs.getInt("post_id"));
                post.setTitle(rs.getString("title"));
                post.setContent(rs.getString("content"));
                post.setReplies(new ArrayList<>());
            }
            int replyId = rs.getInt("reply_id");
            if (replyId != 0) {
                Reply reply = new Reply();
                reply.setId(replyId);
                reply.setContent(rs.getString("reply_content"));
                post.getReplies().add(reply);
            }
        }
        return post;
    }
}
```

### 4) 시사점 및 배운 점
Spring JDBC 사용 시 1:N 조인 데이터를 하나의 객체 그래프로 변환할 때는 RowMapper 대신 ResultSetExtractor를 활용하여 자바 코드에서 수동 매핑 컬렉션을 구성해야 객체 중복 방지 및 정합성 보장 가능.
