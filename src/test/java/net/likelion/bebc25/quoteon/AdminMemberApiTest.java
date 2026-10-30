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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/admin/members/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(put("/api/admin/members/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("이름", "member@quoteon.test", "01012345678", "회사", "ROLE_USER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/admin/members/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/admin/members/1/approve").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/admin/members/1/deactivate").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/admin/members/1/activate").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/admin/members/1/reject").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void regularMemberIsForbidden() throws Exception {
        MockHttpSession session = ActiveSession.open(mockMvc, jdbcTemplate);

        mockMvc.perform(get("/api/admin/members").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/members/1").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/admin/members/1")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("이름", "member@quoteon.test", "01012345678", "회사", "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/members/pending").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/admin/members/1/approve").with(csrf()).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/admin/members/1/deactivate").with(csrf()).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/admin/members/1/activate").with(csrf()).session(session))
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
        mockMvc.perform(put("/api/admin/members/" + target.id())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("이름", target.email(), "01012345678", "토큰회사", "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        mockMvc.perform(post("/api/admin/members/" + target.id() + "/deactivate").session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        mockMvc.perform(post("/api/admin/members/" + target.id() + "/activate").session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));

        assertEquals("PENDING", stored(target.id()).status());
        assertEquals("토큰대상", stored(target.id()).name());
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

    @Test
    void activeAdminCanListEveryMember() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "전체목록관리자");
        String token = token();
        String company = "전체회사" + token;
        Applicant pending = signup("SALES", "대기대상" + token, email(), company);
        Applicant active = signup("SALES", "사용대상" + token, email(), company);
        Applicant rejected = signup("SALES", "거절대상" + token, email(), company);
        Applicant inactive = signup("SALES", "휴면대상" + token, email(), company);
        Applicant adminMember = signup("ADMIN", "운영대상" + token, email(), company);
        assertEquals(1, setStatus(active.id(), "ACTIVE", "2026-02-02 00:00:00"));
        assertEquals(1, setStatus(rejected.id(), "REJECTED", "2026-03-03 00:00:00"));
        assertEquals(1, setStatus(inactive.id(), "INACTIVE", "2026-04-04 00:00:00"));
        assertEquals(1, setStatus(adminMember.id(), "ACTIVE", "2026-05-05 00:00:00"));
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET created_at = '2026-01-01 00:00:00' WHERE id = ?",
                pending.id()
        ));
        String phone = "0199" + token.substring(0, 16);
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET phone = ? WHERE id = ?",
                phone,
                active.id()
        ));
        int total = Math.toIntExact(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member", Long.class));
        int totalPages = total == 0 ? 0 : (total + 19) / 20;

        mockMvc.perform(get("/api/admin/members").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.totalPages").value(totalPages))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdAt").doesNotExist());

        StoredMember activeRow = stored(active.id());
        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("keyword", company))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(adminMember.id())))
                .andExpect(jsonPath("$.items[0].name").value("운영대상" + token))
                .andExpect(jsonPath("$.items[0].email").value(adminMember.email()))
                .andExpect(jsonPath("$.items[0].phone").value("01012345678"))
                .andExpect(jsonPath("$.items[0].companyName").value(company))
                .andExpect(jsonPath("$.items[0].role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[1].id").value(jsonId(inactive.id())))
                .andExpect(jsonPath("$.items[1].role").value("ROLE_USER"))
                .andExpect(jsonPath("$.items[1].status").value("INACTIVE"))
                .andExpect(jsonPath("$.items[2].status").value("REJECTED"))
                .andExpect(jsonPath("$.items[3].id").value(jsonId(active.id())))
                .andExpect(jsonPath("$.items[3].role").value("ROLE_USER"))
                .andExpect(jsonPath("$.items[3].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[3].phone").value(phone))
                .andExpect(jsonPath("$.items[4].id").value(jsonId(pending.id())))
                .andExpect(jsonPath("$.items[4].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdAt").doesNotExist())
                .andExpect(content().string(not(containsString(activeRow.password()))))
                .andExpect(content().string(not(containsString("password1234"))));

        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("keyword", "사용대상" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(active.id())))
                .andExpect(jsonPath("$.items[0].email").value(active.email()));
        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("keyword", rejected.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(rejected.id())))
                .andExpect(jsonPath("$.items[0].name").value("거절대상" + token));
        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("keyword", "없는회원" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("keyword", phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("keyword", company)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(adminMember.id())))
                .andExpect(jsonPath("$.items[1].id").value(jsonId(inactive.id())))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("keyword", company)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(pending.id())))
                .andExpect(jsonPath("$.totalElements").value(5));

        String literalName = "와일드_" + token.substring(0, 8) + "%회원";
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET name = ? WHERE id = ?",
                literalName,
                pending.id()
        ));
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET name = ? WHERE id = ?",
                "와일드X" + token.substring(0, 8) + "Y회원",
                rejected.id()
        ));
        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("keyword", literalName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(pending.id())));

        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/admin/members")
                        .session(admin)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));
    }

    @Test
    void activeAdminCanReadAndUpdateMember() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "상세관리자");
        String token = token();
        Applicant target = signup("SALES", "상세대상", email(), "상세회사");
        Applicant other = signup("SALES", "다른대상", email(), "다른회사");
        StoredMember before = stored(target.id());
        String createdAt = createdAt(target.id());

        mockMvc.perform(get("/api/admin/members/" + target.id()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(target.id())))
                .andExpect(jsonPath("$.name").value("상세대상"))
                .andExpect(jsonPath("$.email").value(target.email()))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.companyName").value("상세회사"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(content().string(not(containsString(before.password()))))
                .andExpect(content().string(not(containsString("password1234"))));

        String changedEmail = email();
        mockMvc.perform(put("/api/admin/members/" + target.id())
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("  수정이름  ", changedEmail, " 01099998888 ", " 수정회사 ", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(target.id())))
                .andExpect(jsonPath("$.name").value("수정이름"))
                .andExpect(jsonPath("$.email").value(changedEmail))
                .andExpect(jsonPath("$.phone").value("01099998888"))
                .andExpect(jsonPath("$.companyName").value("수정회사"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(content().string(not(containsString(before.password()))))
                .andExpect(content().string(not(containsString("password1234"))));

        assertEquals(new StoredMember(
                changedEmail,
                before.password(),
                "수정이름",
                "01099998888",
                "수정회사",
                "ROLE_ADMIN",
                "PENDING"
        ), stored(target.id()));
        assertEquals(createdAt, createdAt(target.id()));

        mockMvc.perform(put("/api/admin/members/" + target.id())
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":1,"name":"유지이름","email":"%s","phone":"","companyName":"   ","role":"ROLE_USER","status":"ACTIVE","password":"new-password"}
                                """.formatted(changedEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("유지이름"))
                .andExpect(jsonPath("$.email").value(changedEmail))
                .andExpect(jsonPath("$.phone").value(nullValue()))
                .andExpect(jsonPath("$.companyName").value(nullValue()))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertEquals(new StoredMember(
                changedEmail,
                before.password(),
                "유지이름",
                null,
                null,
                "ROLE_USER",
                "PENDING"
        ), stored(target.id()));
        assertEquals(createdAt, createdAt(target.id()));

        mockMvc.perform(put("/api/admin/members/" + target.id())
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("유지이름", other.email(), null, null, "ROLE_USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
        assertEquals(changedEmail, stored(target.id()).email());
        assertEquals("유지이름", stored(target.id()).name());
        assertEquals(before.password(), stored(target.id()).password());
        assertEquals("PENDING", stored(target.id()).status());

        long missingId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM member",
                Long.class
        ) + 100_000L;
        mockMvc.perform(get("/api/admin/members/" + missingId).session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));
        mockMvc.perform(put("/api/admin/members/" + missingId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("없는회원", email(), null, null, "ROLE_USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        mockMvc.perform(put("/api/admin/members/" + target.id())
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(" ", changedEmail, "01012345678", "회사", "ROLE_USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("이름을 입력해 주세요."));
        mockMvc.perform(put("/api/admin/members/" + target.id())
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("유지이름", "not-an-email", "01012345678", "회사", "ROLE_USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("이메일 형식을 확인해 주세요."));
        mockMvc.perform(put("/api/admin/members/" + target.id())
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("유지이름", changedEmail, "010123456789012345678", "회사", "ROLE_USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("휴대폰 번호는 20자 이하로 입력해 주세요."));
        mockMvc.perform(put("/api/admin/members/" + target.id())
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("유지이름", changedEmail, "01012345678", "회사", "영업사원")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("권한은 영업사원 또는 관리자만 선택할 수 있습니다."));
        assertEquals(new StoredMember(
                changedEmail,
                before.password(),
                "유지이름",
                null,
                null,
                "ROLE_USER",
                "PENDING"
        ), stored(target.id()));
        assertEquals("PENDING", stored(other.id()).status());
        assertEquals("ROLE_USER", stored(other.id()).role());
    }

    @Test
    void changedOwnRoleIsEnforcedByTheExistingSessionRefresh() throws Exception {
        String adminEmail = email();
        Applicant admin = signup("ADMIN", "본인관리자", adminEmail, "본인회사");
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'",
                admin.id()
        ));
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(adminEmail)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        String password = stored(admin.id()).password();

        mockMvc.perform(put("/api/admin/members/" + admin.id())
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("본인관리자", adminEmail, "01012345678", "본인회사", "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertEquals("ROLE_USER", stored(admin.id()).role());
        assertEquals("ACTIVE", stored(admin.id()).status());
        assertEquals(password, stored(admin.id()).password());

        mockMvc.perform(get("/api/admin/members").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/members").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MvcResult relogin = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(adminEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andReturn();
        MockHttpSession userSession = (MockHttpSession) relogin.getRequest().getSession(false);
        mockMvc.perform(get("/api/admin/members").session(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/categories").session(userSession))
                .andExpect(status().isOk());
    }

    @Test
    void activeAdminCanDeactivateAndActivateMember() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "상태관리자");
        Applicant seller = signup("SALES", "상태대상", email(), "상태회사");
        MockHttpSession sellerSession = activateAndLogin(seller);
        long customerId = insertCustomer(seller.id(), "유지고객");
        insertQuote(seller.id(), customerId, "Q-KEEP-" + token());
        int customers = countRows("customer", seller.id());
        int quotes = countRows("quote", seller.id());
        StoredMember before = stored(seller.id());

        mockMvc.perform(post("/api/admin/members/" + seller.id() + "/activate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_STATUS_CONFLICT"))
                .andExpect(jsonPath("$.message").value("비활성 회원만 활성화할 수 있습니다."));
        assertEquals("ACTIVE", stored(seller.id()).status());

        mockMvc.perform(post("/api/admin/members/" + seller.id() + "/deactivate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(seller.id())))
                .andExpect(jsonPath("$.name").value(before.name()))
                .andExpect(jsonPath("$.email").value(before.email()))
                .andExpect(jsonPath("$.phone").value(before.phone()))
                .andExpect(jsonPath("$.companyName").value(before.companyName()))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(content().string(not(containsString(before.password()))))
                .andExpect(content().string(not(containsString("password1234"))));
        assertEquals(new StoredMember(
                before.email(),
                before.password(),
                before.name(),
                before.phone(),
                before.companyName(),
                before.role(),
                "INACTIVE"
        ), stored(seller.id()));
        assertEquals(customers, countRows("customer", seller.id()));
        assertEquals(quotes, countRows("quote", seller.id()));

        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(sellerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INACTIVE"));
        assertEquals(quotes, countRows("quote", seller.id()));
        assertEquals(true, sellerSession.isInvalid());

        MvcResult blockedLogin = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(seller.email())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INACTIVE"))
                .andExpect(jsonPath("$.message").value("계정이 비활성화되어 서비스를 이용할 수 없습니다."))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        assertEquals(null, blockedLogin.getRequest().getSession(false));

        mockMvc.perform(post("/api/admin/members/" + seller.id() + "/deactivate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_STATUS_CONFLICT"))
                .andExpect(jsonPath("$.message").value("활성 회원만 비활성화할 수 있습니다."));
        assertEquals("INACTIVE", stored(seller.id()).status());
        assertEquals(before.password(), stored(seller.id()).password());

        mockMvc.perform(post("/api/admin/members/" + seller.id() + "/activate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(seller.id())))
                .andExpect(jsonPath("$.email").value(before.email()))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertEquals(new StoredMember(
                before.email(),
                before.password(),
                before.name(),
                before.phone(),
                before.companyName(),
                before.role(),
                "ACTIVE"
        ), stored(seller.id()));
        assertEquals(customers, countRows("customer", seller.id()));
        assertEquals(quotes, countRows("quote", seller.id()));

        mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(seller.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
        mockMvc.perform(post("/api/admin/members/" + seller.id() + "/activate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_STATUS_CONFLICT"));
        assertEquals("ACTIVE", stored(seller.id()).status());
    }

    @Test
    void inactiveMemberCannotReadCustomersOrQuotes() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "조회차단관리자");
        Applicant seller = signup("SALES", "조회대상", email(), "조회회사");
        Applicant quoteReader = signup("SALES", "견적조회대상", email(), "견적회사");
        MockHttpSession sellerSession = activateAndLogin(seller);
        MockHttpSession quoteSession = activateAndLogin(quoteReader);
        mockMvc.perform(post("/api/admin/members/" + seller.id() + "/deactivate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/members/" + quoteReader.id() + "/deactivate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/customers").session(sellerSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INACTIVE"));
        mockMvc.perform(get("/api/quotes").session(quoteSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INACTIVE"));
    }

    @Test
    void pendingAndRejectedMembersCannotBeActivatedOrDeactivated() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "전이관리자");
        Applicant pending = signup("SALES", "대기전이", email(), "대기회사");
        Applicant rejected = signup("SALES", "거절전이", email(), "거절회사");
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET status = 'REJECTED' WHERE id = ? AND status = 'PENDING'",
                rejected.id()
        ));

        mockMvc.perform(post("/api/admin/members/" + pending.id() + "/deactivate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_STATUS_CONFLICT"))
                .andExpect(jsonPath("$.message").value("활성 회원만 비활성화할 수 있습니다."));
        mockMvc.perform(post("/api/admin/members/" + pending.id() + "/activate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_STATUS_CONFLICT"))
                .andExpect(jsonPath("$.message").value("비활성 회원만 활성화할 수 있습니다."));
        assertEquals("PENDING", stored(pending.id()).status());

        mockMvc.perform(post("/api/admin/members/" + rejected.id() + "/deactivate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_STATUS_CONFLICT"));
        mockMvc.perform(post("/api/admin/members/" + rejected.id() + "/activate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_STATUS_CONFLICT"));
        assertEquals("REJECTED", stored(rejected.id()).status());
        assertEquals("ROLE_USER", stored(rejected.id()).role());
    }

    @Test
    void missingMemberStatusChangeReturns404() throws Exception {
        MockHttpSession admin = loginActive("ADMIN", "없는상태관리자");
        long missingId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM member",
                Long.class
        ) + 100_000L;

        mockMvc.perform(post("/api/admin/members/" + missingId + "/deactivate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));
        mockMvc.perform(post("/api/admin/members/" + missingId + "/activate")
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void deactivatingOwnAccountIsBlockedOnTheNextRequest() throws Exception {
        String adminEmail = email();
        Applicant admin = signup("ADMIN", "본인상태", adminEmail, "본인회사");
        MockHttpSession session = activateAndLogin(admin);
        String password = stored(admin.id()).password();

        mockMvc.perform(post("/api/admin/members/" + admin.id() + "/deactivate")
                        .with(csrf())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertEquals("INACTIVE", stored(admin.id()).status());
        assertEquals(password, stored(admin.id()).password());

        mockMvc.perform(get("/api/admin/members").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INACTIVE"));
        assertEquals(true, session.isInvalid());
    }

    private int setStatus(long id, String status, String createdAt) {
        return jdbcTemplate.update(
                "UPDATE member SET status = ?, created_at = ? WHERE id = ? AND status = 'PENDING'",
                status,
                createdAt,
                id
        );
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

    private MockHttpSession activateAndLogin(Applicant applicant) throws Exception {
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'",
                applicant.id()
        );
        if (updated != 1) {
            throw new IllegalStateException("test member was not activated");
        }
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(applicant.email())))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private long insertCustomer(long memberId, String name) {
        jdbcTemplate.update(
                "INSERT INTO customer (member_id, name, customer_type) VALUES (?, ?, '기업')",
                memberId,
                name
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM customer WHERE member_id = ? AND name = ?",
                Long.class,
                memberId,
                name
        );
        if (id == null) {
            throw new IllegalStateException("customer id was not generated");
        }
        return id;
    }

    private void insertQuote(long memberId, long customerId, String quoteNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO quote (quote_number, member_id, customer_id, quote_date)
                VALUES (?, ?, ?, CURRENT_DATE)
                """,
                quoteNumber,
                memberId,
                customerId
        );
    }

    private int countRows(String table, long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE member_id = ?",
                Integer.class,
                memberId
        );
        return count == null ? 0 : count;
    }

    private String createdAt(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(created_at AS CHAR) FROM member WHERE id = ?",
                String.class,
                id
        );
    }

    private static String updateJson(String name, String email, String phone, String companyName, String role) {
        return """
                {"name":"%s","email":"%s","phone":%s,"companyName":%s,"role":"%s"}
                """.formatted(name, email, jsonValue(phone), jsonValue(companyName), role);
    }

    private static String jsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value + "\"";
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
