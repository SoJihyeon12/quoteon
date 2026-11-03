package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import net.likelion.bebc25.quoteon.quote.QuoteMailGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuoteEmailMissingConfigurationTest {

    @DynamicPropertySource
    static void missingMailSettings(DynamicPropertyRegistry registry) {
        registry.add("MAIL_HOST", () -> "");
        registry.add("MAIL_PORT", () -> "");
        registry.add("MAIL_USERNAME", () -> "");
        registry.add("MAIL_PASSWORD", () -> "");
        registry.add("MAIL_PROPERTIES_MAIL_SMTP_AUTH", () -> "");
        registry.add("MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE", () -> "");
        registry.add("quoteon.mail.host", () -> "");
        registry.add("quoteon.mail.port", () -> "");
        registry.add("quoteon.mail.username", () -> "");
        registry.add("quoteon.mail.password", () -> "");
        registry.add("quoteon.mail.smtp-auth", () -> "");
        registry.add("quoteon.mail.starttls-enable", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private QuoteMailGateway quoteMailGateway;

    @Test
    void missingMailSettingsFailBeforeAnyExternalSend() throws Exception {
        SalesMember seller = loginSales();
        long quoteId = createQuote(seller, insertCustomer(seller.id()));

        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"client@example.com","subject":"견적서","content":"내용"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("MAIL_HOST")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("MAIL_PASSWORD")));

        verify(quoteMailGateway, never()).send(any(), anyString(), anyString(), anyString(), any());
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
                "SELECT status FROM quote WHERE id = ?",
                String.class,
                quoteId
        ));
        MapRow history = jdbcTemplate.queryForObject(
                """
                SELECT status, failure_reason, sent_at
                FROM quote_email_history
                WHERE quote_id = ?
                """,
                (rs, rowNum) -> new MapRow(rs.getString("status"), rs.getString("failure_reason"), rs.getTimestamp("sent_at")),
                quoteId
        );
        assertEquals("FAILED", history.status());
        assertNull(history.sentAt());
        assertEquals(true, history.reason().contains("MAIL_USERNAME"));
    }

    private long createQuote(SalesMember seller, long customerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"quoteDate":"2026-10-18","items":[{"productId":%d,"quantity":1}]}
                                """.formatted(customerId, productId("SH-600"))))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private SalesMember loginSales() throws Exception {
        String email = "sales-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"SALES","name":"김영업","email":"%s","password":"password1234","phone":"01012345678","companyName":"테스트회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        jdbcTemplate.update("UPDATE member SET status = 'ACTIVE' WHERE id = ?", id.longValue());
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new SalesMember(id.longValue(), (MockHttpSession) login.getRequest().getSession(false));
    }

    private long insertCustomer(long memberId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO customer (member_id, name, customer_type, status)
                    VALUES (?, '김진우', '기업', 'ACTIVE')
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, memberId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("customer id was not generated");
        }
        return key.longValue();
    }

    private long productId(String productCode) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM product WHERE product_code = ?",
                Long.class,
                productCode
        );
        if (id == null) {
            throw new IllegalStateException("product was not found");
        }
        return id;
    }

    private record SalesMember(long id, MockHttpSession session) {
    }

    private record MapRow(String status, String reason, java.sql.Timestamp sentAt) {
    }
}
