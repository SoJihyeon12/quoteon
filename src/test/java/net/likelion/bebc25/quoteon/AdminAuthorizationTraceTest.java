package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuthorizationTraceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminRequestsShareOneSessionContextAndKeepAdminPrincipal() throws Exception {
        MockHttpSession session = login("ADMIN");
        SecurityContext context = sessionContext(session);
        long adminId = ((Number) JsonPath.read(
                mockMvc.perform(get("/api/me").session(session))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8),
                "$.id"
        )).longValue();

        mockMvc.perform(get("/api/categories").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/products").session(session).param("page", "0").param("size", "10"))
                .andExpect(status().isOk());

        assertSame(context, sessionContext(session));

        AtomicReference<SecurityContext> otherThread = new AtomicReference<>();
        Thread reader = new Thread(() -> otherThread.set(sessionContext(session)));
        reader.start();
        reader.join();
        assertSame(context, otherThread.get());

        mockMvc.perform(get("/api/admin/products").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));

        org.junit.jupiter.api.Assertions.assertTrue(adminId > 0);
    }

    @Test
    void salesProductListDeniedPrincipalIsNotAdmin() throws Exception {
        MockHttpSession admin = login("ADMIN");
        MockHttpSession sales = login("SALES");
        long adminId = memberId(admin);
        long salesId = memberId(sales);

        mockMvc.perform(get("/api/admin/products").session(sales).param("page", "0").param("size", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        org.junit.jupiter.api.Assertions.assertNotEquals(adminId, salesId);
    }

    private long memberId(MockHttpSession session) throws Exception {
        String body = mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private SecurityContext sessionContext(MockHttpSession session) {
        return (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }

    private MockHttpSession login(String accountType) throws Exception {
        String email = accountType.toLowerCase() + "-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"%s","name":"추적회원","email":"%s","password":"password1234","phone":"01012345678","companyName":"추적회사"}
                                """.formatted(accountType, email)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'",
                id.longValue()
        );
        if (updated != 1) {
            throw new IllegalStateException("test member was not activated");
        }
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
