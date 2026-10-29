package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import net.likelion.bebc25.quoteon.support.ActiveSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMemberApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/members/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/admin/members/1/approve").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/admin/members/1/reject").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void regularMemberIsForbidden() throws Exception {
        MockHttpSession session = ActiveSession.open(mockMvc, jdbcTemplate);

        mockMvc.perform(get("/api/admin/members/pending").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/admin/members/1/approve").with(csrf()).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/admin/members/1/reject").with(csrf()).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void pendingAdminIsForbidden() throws Exception {
        Applicant admin = signup("ADMIN", "대기관리자", email(), "대기회사");
        Applicant target = signup("SALES", "대기대상", email(), "대상회사");

        mockMvc.perform(get("/api/admin/members/pending").session(admin.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PENDING"));
        mockMvc.perform(post("/api/admin/members/" + target.id() + "/approve")
                        .with(csrf())
                        .session(admin.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PENDING"));
        mockMvc.perform(post("/api/admin/members/" + target.id() + "/reject")
                        .with(csrf())
                        .session(admin.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PENDING"));

        assertEquals("PENDING", stored(target.id()).status());
        assertEquals("ROLE_USER", stored(target.id()).role());
    }

    @Test
    void missingCsrfIsForbidden() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "승인관리자");
        Applicant target = signup("SALES", "토큰대상", email(), "토큰회사");

        mockMvc.perform(post("/api/admin/members/" + target.id() + "/approve").session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        mockMvc.perform(post("/api/admin/members/" + target.id() + "/reject").session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));

        assertEquals("PENDING", stored(target.id()).status());
    }

    @Test
    void activeAdminCanReadPendingMembers() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "목록관리자");
        String token = token();
        Applicant pending = signup("SALES", "목록대상", email(), "목록" + token);
        Applicant active = signup("SALES", "완료대상", email(), "목록" + token);
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE', created_at = '2026-03-01 09:30:00' WHERE id = ? AND status = 'PENDING'",
                active.id()
        ));
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET created_at = '2026-03-01 09:30:00' WHERE id = ?",
                pending.id()
        ));

        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("keyword", pending.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(pending.id())))
                .andExpect(jsonPath("$.items[0].name").value("목록대상"))
                .andExpect(jsonPath("$.items[0].email").value(pending.email()))
                .andExpect(jsonPath("$.items[0].phone").value("01012345678"))
                .andExpect(jsonPath("$.items[0].companyName").value("목록" + token))
                .andExpect(jsonPath("$.items[0].role").value("ROLE_USER"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-03-01T09:30:00"))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").doesNotExist());

        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("keyword", "목록" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(pending.id())))
                .andExpect(jsonPath("$.items[0].password").doesNotExist());
    }

    @Test
    void activeAdminCanApproveAndReject() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "처리관리자");
        Applicant approved = signup("SALES", "승인대상", email(), "승인회사");
        StoredMember beforeApprove = stored(approved.id());

        mockMvc.perform(post("/api/admin/members/" + approved.id() + "/approve")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(approved.id())))
                .andExpect(jsonPath("$.email").value(approved.email()))
                .andExpect(jsonPath("$.name").value("승인대상"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.companyName").value("승인회사"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString(beforeApprove.password()))))
                .andExpect(content().string(not(containsString("password1234"))));

        assertEquals(new StoredMember(
                beforeApprove.email(),
                beforeApprove.password(),
                beforeApprove.name(),
                beforeApprove.phone(),
                beforeApprove.companyName(),
                beforeApprove.role(),
                "ACTIVE"
        ), stored(approved.id()));

        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(approved.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        MockHttpSession approvedSession = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/categories").session(approvedSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products").session(approvedSession))
                .andExpect(status().isOk());

        Applicant rejected = signup("SALES", "거절대상", email(), "거절회사");
        StoredMember beforeReject = stored(rejected.id());
        mockMvc.perform(post("/api/admin/members/" + rejected.id() + "/reject")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(rejected.id())))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString(beforeReject.password()))))
                .andExpect(content().string(not(containsString("password1234"))));
        assertEquals(new StoredMember(
                beforeReject.email(),
                beforeReject.password(),
                beforeReject.name(),
                beforeReject.phone(),
                beforeReject.companyName(),
                beforeReject.role(),
                "REJECTED"
        ), stored(rejected.id()));
        mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(rejected.email())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REJECTED"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void missingMemberReturns404() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "없는회원관리자");
        long missingId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM member",
                Long.class
        ) + 100_000L;

        mockMvc.perform(post("/api/admin/members/" + missingId + "/approve")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));
        mockMvc.perform(post("/api/admin/members/" + missingId + "/reject")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void processedMemberReturns409() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "중복처리관리자");
        Applicant approved = signup("SALES", "중복승인", email(), "중복승인회사");
        mockMvc.perform(post("/api/admin/members/" + approved.id() + "/approve")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/members/" + approved.id() + "/approve")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_PENDING"))
                .andExpect(jsonPath("$.message").value("승인 대기 상태의 회원만 처리할 수 있습니다."));
        mockMvc.perform(post("/api/admin/members/" + approved.id() + "/reject")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_PENDING"));
        assertEquals("ACTIVE", stored(approved.id()).status());

        Applicant rejected = signup("SALES", "중복거절", email(), "중복거절회사");
        mockMvc.perform(post("/api/admin/members/" + rejected.id() + "/reject")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/members/" + rejected.id() + "/reject")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_PENDING"));
        mockMvc.perform(post("/api/admin/members/" + rejected.id() + "/approve")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_PENDING"));
        assertEquals("REJECTED", stored(rejected.id()).status());
    }

    @Test
    void searchAndPagination() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "검색관리자");
        String token = token();
        Applicant older = signup("SALES", "오래된" + token, email(), "페이지" + token);
        Applicant middle = signup("SALES", "중간" + token, email(), "페이지" + token);
        Applicant newer = signup("SALES", "최근" + token, email(), "페이지" + token);
        Applicant other = signup("SALES", "다른회원", email(), "다른" + token);
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET created_at = '2024-01-01 00:00:00' WHERE id = ?",
                older.id()
        ));
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET created_at = '2025-01-01 00:00:00' WHERE id = ?",
                middle.id()
        ));
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET created_at = '2025-01-01 00:00:00' WHERE id = ?",
                newer.id()
        ));

        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("keyword", "페이지" + token)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(newer.id())))
                .andExpect(jsonPath("$.items[1].id").value(jsonId(middle.id())))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].password").doesNotExist());
        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("keyword", "페이지" + token)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(older.id())))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("keyword", middle.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(middle.id())));
        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("keyword", "중간" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value(middle.email()));

        String literalName = "와일드_" + token + "%대상";
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET name = ? WHERE id = ?",
                literalName,
                other.id()
        ));
        Applicant wildcard = signup("SALES", "와일드대상", email(), "와일드회사" + token);
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET name = ? WHERE id = ?",
                "와일드X" + token + "Y대상",
                wildcard.id()
        ));
        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("keyword", literalName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(other.id())));

        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/admin/members/pending")
                        .session(admin)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Applicant signup(String accountType, String name, String email, String company) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"%s","name":"%s","email":"%s","password":"password1234","phone":"01012345678","companyName":"%s"}
                                """.formatted(accountType, name, email, company)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return new Applicant(
                id.longValue(),
                email,
                (MockHttpSession) result.getRequest().getSession(false)
        );
    }

    private MockHttpSession loginActive(String accountType, String name) throws Exception {
        String email = email();
        Applicant applicant = signup(accountType, name, email, "관리회사");
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING' AND email = ?",
                applicant.id(),
                email
        );
        if (updated != 1) {
            throw new IllegalStateException("test member was not activated");
        }
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private StoredMember stored(long id) {
        return jdbcTemplate.queryForObject("""
                SELECT email, password, name, phone, company_name, role, status
                FROM member
                WHERE id = ?
                """, (rs, rowNum) -> new StoredMember(
                rs.getString("email"),
                rs.getString("password"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("company_name"),
                rs.getString("role"),
                rs.getString("status")
        ), id);
    }

    private static String loginJson(String email) {
        return """
                {"email":"%s","password":"password1234"}
                """.formatted(email);
    }

    private static String email() {
        return "member-" + UUID.randomUUID() + "@quoteon.test";
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static int jsonId(long id) {
        return Math.toIntExact(id);
    }

    private record Applicant(long id, String email, MockHttpSession session) {
    }

    private record StoredMember(
            String email,
            String password,
            String name,
            String phone,
            String companyName,
            String role,
            String status
    ) {
    }
}
