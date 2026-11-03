package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import net.likelion.bebc25.quoteon.quote.QuoteEmailRepository;
import net.likelion.bebc25.quoteon.quote.QuoteFile;
import net.likelion.bebc25.quoteon.quote.QuoteMailGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuoteEmailApiTest {

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("MAIL_HOST", () -> "mail.test.invalid");
        registry.add("MAIL_PORT", () -> "2525");
        registry.add("MAIL_USERNAME", () -> "sender@example.com");
        registry.add("MAIL_PASSWORD", () -> "mail-test");
        registry.add("MAIL_PROPERTIES_MAIL_SMTP_AUTH", () -> "true");
        registry.add("MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private QuoteMailGateway quoteMailGateway;

    @MockitoSpyBean
    private QuoteEmailRepository quoteEmailRepository;

    @Test
    void ownerSendsPdfToTheRequestedRecipientAndMarksTheQuoteSent() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));
        LocalDateTime before = updatedAt(quoteId);
        assertTrue(mockingDetails(quoteMailGateway).isMock());

        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"client@example.com","subject":"신발장 견적서","content":"상담 내용을 첨부합니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(jsonId(quoteId)))
                .andExpect(jsonPath("$.status").value("SENT"));

        ArgumentCaptor<QuoteFile> pdf = ArgumentCaptor.forClass(QuoteFile.class);
        verify(quoteMailGateway).send(
                any(),
                eq("client@example.com"),
                eq("신발장 견적서"),
                eq("상담 내용을 첨부합니다."),
                pdf.capture()
        );
        assertEquals("견적서_" + quoteNumber(quoteId) + ".pdf", pdf.getValue().filename());
        assertEquals("%PDF", new String(pdf.getValue().bytes(), 0, 4, StandardCharsets.US_ASCII));
        assertEquals("SENT", statusOf(quoteId));
        assertTrue(updatedAt(quoteId).isAfter(before) || updatedAt(quoteId).equals(before));
        Map<String, Object> history = history(quoteId);
        assertEquals("client@example.com", history.get("recipient_email"));
        assertEquals("신발장 견적서", history.get("subject"));
        assertEquals("SUCCESS", history.get("status"));
        assertNull(history.get("failure_reason"));
        assertNotNull(history.get("sent_at"));
    }

    @Test
    void blankSubjectAndContentAreAcceptedBecauseTheDocumentDoesNotRequireThem() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));

        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"client@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));

        verify(quoteMailGateway).send(any(), eq("client@example.com"), eq(""), eq(""), any());
        assertNull(history(quoteId).get("subject"));
    }

    @Test
    void otherMemberAnonymousAndMissingQuoteAreRejectedWithoutSending() throws Exception {
        SalesMember seller = loginSales("김영업");
        SalesMember other = loginSales("이상담");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), "customer@quoteon.test"), itemJson(productId("SH-600"), 1));
        String body = """
                {"recipient":"client@example.com","subject":"견적서","content":"내용"}
                """;

        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(other.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_ACCESS_DENIED"));
        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/quotes/{id}/email", 999999999L)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        verify(quoteMailGateway, never()).send(any(), anyString(), anyString(), anyString(), any());
        assertEquals(0, historyCount(quoteId));
    }

    @Test
    void invalidRecipientAndTooLongSubjectDoNotSend() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));
        LocalDateTime updatedAt = updatedAt(quoteId);

        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"not-an-email","subject":"견적서","content":"내용"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"","subject":"견적서","content":"내용"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"client@example.com","subject":"%s","content":"내용"}
                                """.formatted("가".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("이메일 제목은 255자 이하로 입력해 주세요."));

        verify(quoteMailGateway, never()).send(any(), anyString(), anyString(), anyString(), any());
        assertEquals("DRAFT", statusOf(quoteId));
        assertEquals(updatedAt, updatedAt(quoteId));
        assertEquals(0, historyCount(quoteId));
    }

    @Test
    void smtpFailureKeepsTheQuoteStatusAndStoresTheFailure() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));
        LocalDateTime updatedAt = updatedAt(quoteId);
        doThrow(new MailSendException("smtp down")).when(quoteMailGateway)
                .send(any(), anyString(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"client@example.com","subject":"신발장 견적서","content":"내용"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EMAIL_SEND_FAILED"));

        assertEquals("DRAFT", statusOf(quoteId));
        assertEquals(updatedAt, updatedAt(quoteId));
        Map<String, Object> history = history(quoteId);
        assertEquals("FAILED", history.get("status"));
        assertEquals("smtp down", history.get("failure_reason"));
        assertNull(history.get("sent_at"));
        assertEquals("client@example.com", history.get("recipient_email"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseFailureAfterSendDoesNotMarkTheQuoteSent() throws Exception {
        SalesMember seller = loginSales("김영업");
        long customerId = insertCustomer(seller.id(), null);
        long quoteId = createQuote(seller, customerId, itemJson(productId("SH-600"), 1));
        try {
            doThrow(new DataAccessResourceFailureException("db down"))
                    .when(quoteEmailRepository)
                    .markSent(quoteId, seller.id());

            mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                            .with(csrf())
                            .session(seller.session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"recipient":"client@example.com","subject":"신발장 견적서","content":"내용"}
                                    """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("EMAIL_STATUS_NOT_SAVED"));

            verify(quoteMailGateway).send(any(), eq("client@example.com"), anyString(), anyString(), any());
            assertEquals("DRAFT", statusOf(quoteId));
            assertEquals(0, historyCount(quoteId));
        } finally {
            jdbcTemplate.update("DELETE FROM quote_email_history WHERE quote_id = ?", quoteId);
            jdbcTemplate.update("DELETE FROM quote_item WHERE quote_id = ?", quoteId);
            jdbcTemplate.update("DELETE FROM quote WHERE id = ?", quoteId);
            jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customerId);
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", seller.id());
            reset(quoteEmailRepository);
        }
    }

    @Test
    void existingQuotePdfAndUpdateStayAvailable() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), "customer@quoteon.test"), itemJson(productId("SH-600"), 1));

        mockMvc.perform(get("/api/quotes/{id}/pdf", quoteId).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        MediaType.APPLICATION_PDF_VALUE,
                        result.getResponse().getContentType()
                ));
        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"quoteDate":"2026-10-18","paymentMethod":"CASH","items":[%s]}
                                """.formatted(insertCustomer(seller.id(), null), itemJson(productId("SH-800"), 1))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"client@example.com","subject":"견적서","content":"내용"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"quoteDate":"2026-10-19","paymentMethod":"CASH","items":[%s]}
                                """.formatted(insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_EDITABLE"));
        verify(quoteMailGateway).send(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void ownerReadsEachEmailAttemptWithTheNewestAttemptFirst() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));

        mockMvc.perform(get("/api/quotes/{id}/email/histories", quoteId).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        doThrow(new MailSendException("smtp down")).when(quoteMailGateway)
                .send(any(), anyString(), anyString(), anyString(), any());
        sendEmail(seller, quoteId, "client@example.com", "첫 실패", "첫 내용")
                .andExpect(status().isInternalServerError());
        reset(quoteMailGateway);
        sendEmail(seller, quoteId, "later@example.com", "두 번째 성공", "두 번째 내용")
                .andExpect(status().isOk());

        long failedId = historyId(quoteId, "FAILED");
        long successId = historyId(quoteId, "SUCCESS");
        jdbcTemplate.update(
                "UPDATE quote_email_history SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 11, 2, 9, 0)),
                successId
        );
        jdbcTemplate.update(
                "UPDATE quote_email_history SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 11, 2, 10, 0)),
                failedId
        );

        mockMvc.perform(get("/api/quotes/{id}/email/histories", quoteId).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(failedId)))
                .andExpect(jsonPath("$.items[0].recipientEmail").value("client@example.com"))
                .andExpect(jsonPath("$.items[0].subject").value("첫 실패"))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].sentAt").value(nullValue()))
                .andExpect(jsonPath("$.items[0].failureReason").value("smtp down"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-02T10:00:00"))
                .andExpect(jsonPath("$.items[1].id").value(jsonId(successId)))
                .andExpect(jsonPath("$.items[1].recipientEmail").value("later@example.com"))
                .andExpect(jsonPath("$.items[1].subject").value("두 번째 성공"))
                .andExpect(jsonPath("$.items[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[1].failureReason").value(nullValue()))
                .andExpect(jsonPath("$.items[1].sentAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-11-02T09:00:00"));
    }

    @Test
    void anotherMemberAnonymousAndMissingQuoteCannotReadEmailHistories() throws Exception {
        SalesMember seller = loginSales("김영업");
        SalesMember other = loginSales("이상담");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));

        mockMvc.perform(get("/api/quotes/{id}/email/histories", quoteId).session(other.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_ACCESS_DENIED"));
        mockMvc.perform(get("/api/quotes/{id}/email/histories", quoteId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/quotes/{id}/email/histories", 999999999L).session(seller.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
    }

    @Test
    void failedHistoryRetrySendsAgainAndKeepsThePreviousHistory() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));
        doThrow(new MailSendException("smtp down")).when(quoteMailGateway)
                .send(any(), anyString(), anyString(), anyString(), any());
        sendEmail(seller, quoteId, "client@example.com", "첫 실패", "첫 내용")
                .andExpect(status().isInternalServerError());
        long failedId = historyId(quoteId, "FAILED");
        reset(quoteMailGateway);

        mockMvc.perform(post("/api/quotes/{id}/email/histories/{historyId}/retry", quoteId, failedId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"retry@example.com","subject":"재전송","content":"다시 보냅니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(jsonId(quoteId)))
                .andExpect(jsonPath("$.status").value("SENT"));

        ArgumentCaptor<QuoteFile> pdf = ArgumentCaptor.forClass(QuoteFile.class);
        verify(quoteMailGateway).send(any(), eq("retry@example.com"), eq("재전송"), eq("다시 보냅니다."), pdf.capture());
        assertEquals("%PDF", new String(pdf.getValue().bytes(), 0, 4, StandardCharsets.US_ASCII));
        assertEquals("SENT", statusOf(quoteId));
        assertEquals(2, historyCount(quoteId));
        Map<String, Object> previous = historyById(failedId);
        assertEquals("FAILED", previous.get("status"));
        assertEquals("smtp down", previous.get("failure_reason"));
        assertEquals("client@example.com", previous.get("recipient_email"));
        Map<String, Object> retried = historyById(historyId(quoteId, "SUCCESS"));
        assertEquals("retry@example.com", retried.get("recipient_email"));
        assertEquals("재전송", retried.get("subject"));
        assertNull(retried.get("failure_reason"));
        assertNotNull(retried.get("sent_at"));
    }

    @Test
    void failedRetryStoresANewFailureAndLeavesTheQuoteAndPreviousHistory() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));
        LocalDateTime updatedAt = updatedAt(quoteId);
        doThrow(new MailSendException("smtp down")).when(quoteMailGateway)
                .send(any(), anyString(), anyString(), anyString(), any());
        sendEmail(seller, quoteId, "client@example.com", "첫 실패", "첫 내용")
                .andExpect(status().isInternalServerError());
        long failedId = historyId(quoteId, "FAILED");
        reset(quoteMailGateway);
        doThrow(new MailSendException("smtp down again")).when(quoteMailGateway)
                .send(any(), anyString(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/quotes/{id}/email/histories/{historyId}/retry", quoteId, failedId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"retry@example.com","subject":"재전송","content":"다시 보냅니다."}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EMAIL_SEND_FAILED"));

        assertEquals("DRAFT", statusOf(quoteId));
        assertEquals(updatedAt, updatedAt(quoteId));
        assertEquals(2, historyCount(quoteId));
        Map<String, Object> previous = historyById(failedId);
        assertEquals("FAILED", previous.get("status"));
        assertEquals("smtp down", previous.get("failure_reason"));
        Map<String, Object> retried = latestHistory(quoteId);
        assertEquals("FAILED", retried.get("status"));
        assertEquals("smtp down again", retried.get("failure_reason"));
        assertEquals("retry@example.com", retried.get("recipient_email"));
        assertNull(retried.get("sent_at"));
    }

    @Test
    void successfulHistoryAndAnotherMembersHistoryCannotBeRetried() throws Exception {
        SalesMember seller = loginSales("김영업");
        SalesMember other = loginSales("이상담");
        long quoteId = createQuote(seller, insertCustomer(seller.id(), null), itemJson(productId("SH-600"), 1));
        long otherQuoteId = createQuote(other, insertCustomer(other.id(), null), itemJson(productId("SH-600"), 1));
        sendEmail(seller, quoteId, "client@example.com", "성공", "내용").andExpect(status().isOk());
        long successId = historyId(quoteId, "SUCCESS");
        doThrow(new MailSendException("smtp down")).when(quoteMailGateway)
                .send(any(), anyString(), anyString(), anyString(), any());
        sendEmail(other, otherQuoteId, "other@example.com", "실패", "내용")
                .andExpect(status().isInternalServerError());
        long otherFailedId = historyId(otherQuoteId, "FAILED");
        reset(quoteMailGateway);
        String body = """
                {"recipient":"retry@example.com","subject":"재전송","content":"다시 보냅니다."}
                """;

        mockMvc.perform(post("/api/quotes/{id}/email/histories/{historyId}/retry", quoteId, successId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_RETRY_NOT_ALLOWED"));
        mockMvc.perform(post("/api/quotes/{id}/email/histories/{historyId}/retry", quoteId, otherFailedId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMAIL_HISTORY_NOT_FOUND"));
        mockMvc.perform(post("/api/quotes/{id}/email/histories/{historyId}/retry", otherQuoteId, otherFailedId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_ACCESS_DENIED"));
        mockMvc.perform(post("/api/quotes/{id}/email/histories/{historyId}/retry", quoteId, 999999999L)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMAIL_HISTORY_NOT_FOUND"));
        mockMvc.perform(post("/api/quotes/{id}/email/histories/{historyId}/retry", quoteId, successId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(quoteMailGateway, never()).send(any(), anyString(), anyString(), anyString(), any());
        assertEquals(1, historyCount(quoteId));
        assertEquals("SUCCESS", historyById(successId).get("status"));
        assertEquals("FAILED", historyById(otherFailedId).get("status"));
        assertEquals("SENT", statusOf(quoteId));
        assertEquals("DRAFT", statusOf(otherQuoteId));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseFailureAfterRetryDoesNotMarkTheQuoteSentOrRewriteTheFailedHistory() throws Exception {
        SalesMember seller = loginSales("김영업");
        long customerId = insertCustomer(seller.id(), null);
        long quoteId = createQuote(seller, customerId, itemJson(productId("SH-600"), 1));
        try {
            doThrow(new MailSendException("smtp down")).when(quoteMailGateway)
                    .send(any(), anyString(), anyString(), anyString(), any());
            sendEmail(seller, quoteId, "client@example.com", "첫 실패", "첫 내용")
                    .andExpect(status().isInternalServerError());
            long failedId = historyId(quoteId, "FAILED");
            reset(quoteMailGateway);
            doThrow(new DataAccessResourceFailureException("db down"))
                    .when(quoteEmailRepository)
                    .markSent(quoteId, seller.id());

            mockMvc.perform(post("/api/quotes/{id}/email/histories/{historyId}/retry", quoteId, failedId)
                            .with(csrf())
                            .session(seller.session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"recipient":"retry@example.com","subject":"재전송","content":"다시 보냅니다."}
                                    """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("EMAIL_STATUS_NOT_SAVED"));

            verify(quoteMailGateway).send(any(), eq("retry@example.com"), eq("재전송"), eq("다시 보냅니다."), any());
            assertEquals("DRAFT", statusOf(quoteId));
            assertEquals(1, historyCount(quoteId));
            Map<String, Object> previous = historyById(failedId);
            assertEquals("FAILED", previous.get("status"));
            assertEquals("smtp down", previous.get("failure_reason"));
        } finally {
            jdbcTemplate.update("DELETE FROM quote_email_history WHERE quote_id = ?", quoteId);
            jdbcTemplate.update("DELETE FROM quote_item WHERE quote_id = ?", quoteId);
            jdbcTemplate.update("DELETE FROM quote WHERE id = ?", quoteId);
            jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customerId);
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", seller.id());
            reset(quoteEmailRepository);
            reset(quoteMailGateway);
        }
    }

    private org.springframework.test.web.servlet.ResultActions sendEmail(
            SalesMember seller,
            long quoteId,
            String recipient,
            String subject,
            String content
    ) throws Exception {
        return mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                .with(csrf())
                .session(seller.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"recipient":"%s","subject":"%s","content":"%s"}
                        """.formatted(recipient, subject, content)));
    }

    private long historyId(long quoteId, String status) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM quote_email_history WHERE quote_id = ? AND status = ?",
                Long.class,
                quoteId,
                status
        );
        if (id == null) {
            throw new IllegalStateException("email history was not found");
        }
        return id;
    }

    private Map<String, Object> historyById(long historyId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT recipient_email, subject, status, failure_reason, sent_at
                FROM quote_email_history
                WHERE id = ?
                """,
                historyId
        );
    }

    private Map<String, Object> latestHistory(long quoteId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT recipient_email, subject, status, failure_reason, sent_at
                FROM quote_email_history
                WHERE quote_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                quoteId
        );
    }

    private Map<String, Object> history(long quoteId) {
        return jdbcTemplate.queryForMap(
                """
                SELECT recipient_email, subject, status, failure_reason, sent_at
                FROM quote_email_history
                WHERE quote_id = ?
                """,
                quoteId
        );
    }

    private int historyCount(long quoteId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quote_email_history WHERE quote_id = ?",
                Integer.class,
                quoteId
        );
        return count == null ? 0 : count;
    }

    private String statusOf(long quoteId) {
        return jdbcTemplate.queryForObject("SELECT status FROM quote WHERE id = ?", String.class, quoteId);
    }

    private LocalDateTime updatedAt(long quoteId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM quote WHERE id = ?",
                Timestamp.class,
                quoteId
        );
        if (timestamp == null) {
            throw new IllegalStateException("quote updated_at was not found");
        }
        return timestamp.toLocalDateTime();
    }

    private String quoteNumber(long quoteId) {
        return jdbcTemplate.queryForObject("SELECT quote_number FROM quote WHERE id = ?", String.class, quoteId);
    }

    private long createQuote(SalesMember seller, long customerId, String items) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"quoteDate":"2026-10-18","paymentMethod":"CASH","items":[%s]}
                                """.formatted(customerId, items)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private SalesMember loginSales(String name) throws Exception {
        String email = "sales-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"SALES","name":"%s","email":"%s","password":"password1234","phone":"01012345678","companyName":"테스트회사"}
                                """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idOf(signup);
        activate(id);
        return new SalesMember(id, login(email));
    }

    private void activate(long id) {
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'",
                id
        );
        if (updated != 1) {
            throw new IllegalStateException("test member was not activated");
        }
    }

    private MockHttpSession login(String email) throws Exception {
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

    private long insertCustomer(long memberId, String email) {
        String sql = """
                INSERT INTO customer (member_id, name, customer_type, phone, email, address, memo, status)
                VALUES (?, '김진우', '기업', '01012345678', ?, '서울시 강남구', '고객 메모', 'ACTIVE')
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, memberId);
            statement.setString(2, email);
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

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private static String itemJson(long productId, int quantity) {
        return """
                {"productId":%d,"quantity":%d}
                """.formatted(productId, quantity);
    }

    private static int jsonId(long id) {
        return Math.toIntExact(id);
    }

    private record SalesMember(long id, MockHttpSession session) {
    }
}
