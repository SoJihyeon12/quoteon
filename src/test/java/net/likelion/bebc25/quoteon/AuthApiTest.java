package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void signupStoresPendingBcryptHashAndDoesNotReturnPassword() throws Exception {
        String email = email();
        MvcResult result = signup(email, "password1234");
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM member WHERE id = ?",
                String.class,
                id.longValue()
        );
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password FROM member WHERE id = ?",
                String.class,
                id.longValue()
        );
        String storedRole = jdbcTemplate.queryForObject(
                "SELECT role FROM member WHERE id = ?",
                String.class,
                id.longValue()
        );

        assertEquals("PENDING", storedStatus);
        assertEquals("ROLE_USER", storedRole);
        assertNotEquals("password1234", storedHash);
        assertTrue(storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"));
        assertTrue(passwordEncoder.matches("password1234", storedHash));
    }

    @Test
    void unknownEmailAndWrongPasswordReturnTheSame401() throws Exception {
        String email = email();
        signup(email, "password1234");

        MvcResult missing = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email() + ".missing")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
                .andReturn();
        MvcResult wrong = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."))
                .andReturn();

        assertEquals(
                missing.getResponse().getContentAsString(StandardCharsets.UTF_8),
                wrong.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    @Test
    void pendingMemberCanReadProfileAndLogoutButNotCatalog() throws Exception {
        MvcResult signup = signup(email(), "password1234");
        MockHttpSession session = (MockHttpSession) signup.getRequest().getSession(false);

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/api/categories").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PENDING"));
        mockMvc.perform(get("/api/products").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PENDING"));
        mockMvc.perform(post("/api/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void activeMemberCanReadCategoriesAndProducts() throws Exception {
        String email = email();
        MvcResult signup = signup(email, "password1234");
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        assertEquals(1, jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING' AND email = ?",
                id.longValue(),
                email
        ));

        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/categories").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("전자제품"));
        mockMvc.perform(get("/api/products").session(session).param("keyword", "SH-600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productCode").value("SH-600"));
    }

    @Test
    void rejectedMemberDoesNotReceiveASession() throws Exception {
        String email = email();
        MvcResult signup = signup(email, "password1234");
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        jdbcTemplate.update(
                "UPDATE member SET status = 'REJECTED' WHERE id = ? AND email = ?",
                id.longValue(),
                email
        );

        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REJECTED"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        assertNull(login.getRequest().getSession(false));
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void inactiveMemberCannotUseProtectedApis() throws Exception {
        String email = email();
        MvcResult signup = signup(email, "password1234");
        MockHttpSession session = (MockHttpSession) signup.getRequest().getSession(false);
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        jdbcTemplate.update(
                "UPDATE member SET status = 'INACTIVE' WHERE id = ? AND email = ?",
                id.longValue(),
                email
        );

        mockMvc.perform(get("/api/products").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INACTIVE"));
        mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void missingSessionAndMissingCsrfAreRejected() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@quoteon.test\",\"password\":\"password1234\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        mockMvc.perform(get("/api/categories").header("Origin", "http://127.0.0.1:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
    }

    private MvcResult signup(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"SALES","name":"테스트회원","email":"%s","password":"%s","phone":"01012345678","companyName":"테스트회사"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        return result;
    }

    private static String email() {
        return "member-" + UUID.randomUUID() + "@quoteon.test";
    }
}
