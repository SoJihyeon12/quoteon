package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import net.likelion.bebc25.quoteon.quote.QuoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuoteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private QuoteRepository quoteRepository;

    @AfterEach
    void resetQuoteRepository() {
        reset(quoteRepository);
    }

    @Test
    void createsQuoteFromDatabasePrices() throws Exception {
        SalesMember seller = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "견적고객");
        long shoeId = productId("SH-600");
        long deskId = productId("OF-100");

        MvcResult result = mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(shoeId, 2) + "," + itemJson(deskId, 1))
                                .replace("{\"customerId\"", "{\"memberId\":999999999,\"customerId\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quoteNumber", startsWith("Q-20261029-")))
                .andExpect(jsonPath("$.customerId").value(jsonId(customerId)))
                .andExpect(jsonPath("$.supplyAmount").value(375000.00))
                .andExpect(jsonPath("$.vatAmount").value(37500))
                .andExpect(jsonPath("$.totalAmount").value(412500.00))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.quoteDate").value("2026-10-29"))
                .andExpect(jsonPath("$.items[0].productId").value(jsonId(shoeId)))
                .andExpect(jsonPath("$.items[0].productName").value("화이트 신발장"))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[0].salePrice").value(120000.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].amount").value(240000.00))
                .andExpect(jsonPath("$.items[1].productCode").value("OF-100"))
                .andExpect(jsonPath("$.items[1].quantity").value(1))
                .andExpect(jsonPath("$.items[1].amount").value(135000.00))
                .andReturn();
        Number quoteId = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        StoredQuote stored = storedQuote(quoteId.longValue());
        assertEquals(seller.id(), stored.memberId());
        assertEquals(customerId, stored.customerId());
        assertEquals("DRAFT", stored.status());
        assertEquals(0, new BigDecimal("375000.00").compareTo(stored.supplyAmount()));
        assertEquals(0, new BigDecimal("37500").compareTo(stored.vatAmount()));
        assertEquals(0, new BigDecimal("412500.00").compareTo(stored.totalAmount()));
        assertEquals(shoeId, storedItemProductId(quoteId.longValue()));

        MvcResult second = mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(deskId, 1))))
                .andExpect(status().isCreated())
                .andReturn();
        String firstNumber = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.quoteNumber");
        String secondNumber = JsonPath.read(second.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.quoteNumber");
        assertNotEquals(firstNumber, secondNumber);
    }

    @Test
    void vatRoundsHalfUpToWon() throws Exception {
        SalesMember seller = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "반올림고객");
        long productId = insertProduct("VAT-" + token(), "반올림제품", "15.00", "ACTIVE");

        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(productId, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplyAmount").value(15.00))
                .andExpect(jsonPath("$.vatAmount").value(2))
                .andExpect(jsonPath("$.totalAmount").value(17.00));
    }

    @Test
    void rejectsAnotherMembersCustomer() throws Exception {
        SalesMember owner = loginActiveSales();
        SalesMember other = loginActiveSales();
        long customerId = insertCustomer(owner.id(), "타인고객");

        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(other.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(productId("SH-600"), 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ACCESS_DENIED"));
        assertEquals(0, quoteCount(customerId));
    }

    @Test
    void rejectsMissingCustomerAndProduct() throws Exception {
        SalesMember seller = loginActiveSales();
        long missingCustomerId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM customer",
                Long.class
        ) + 100_000L;
        long missingProductId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM product",
                Long.class
        ) + 100_000L;
        long customerId = insertCustomer(seller.id(), "없는제품고객");

        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(missingCustomerId, "2026-10-29", itemJson(productId("SH-600"), 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(missingProductId, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertEquals(0, quoteCount(customerId));
    }

    @Test
    void rejectsInactiveProduct() throws Exception {
        SalesMember seller = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "중지제품고객");
        long productId = insertProduct("STOP-" + token(), "중지제품", "1000.00", "INACTIVE");

        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(productId, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ACTIVE"));
        assertEquals(0, quoteCount(customerId));
    }

    @Test
    void rejectsInvalidQuantityEmptyItemsAndDuplicates() throws Exception {
        SalesMember seller = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "검증고객");
        long productId = productId("SH-600");

        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(productId, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("수량은 1 이상이어야 합니다."));
        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"quoteDate":"2026-10-29","items":[]}
                                """.formatted(customerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("견적 항목을 한 개 이상 입력해 주세요."));
        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(productId, 1) + "," + itemJson(productId, 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT"));
        assertEquals(0, quoteCount(customerId));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackQuoteWhenItemInsertFails() throws Exception {
        SalesMember seller = null;
        Long customerId = null;
        try {
            seller = loginActiveSales();
            customerId = insertCustomer(seller.id(), "롤백고객");
            doThrow(new DataIntegrityViolationException("quote item insert failed"))
                    .when(quoteRepository)
                    .insertItems(anyLong(), anyList());

            mockMvc.perform(post("/api/quotes")
                            .with(csrf())
                            .session(seller.session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(quoteJson(customerId, "2026-10-29", itemJson(productId("SH-600"), 1))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("DATA_ACCESS_ERROR"));
            assertEquals(0, quoteCount(customerId));
        } finally {
            if (customerId != null) {
                jdbcTemplate.update("""
                        DELETE quote_item FROM quote_item
                        INNER JOIN quote ON quote.id = quote_item.quote_id
                        WHERE quote.customer_id = ?
                        """, customerId);
                jdbcTemplate.update("DELETE FROM quote WHERE customer_id = ?", customerId);
                jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customerId);
            }
            if (seller != null) {
                jdbcTemplate.update("DELETE FROM member WHERE id = ?", seller.id());
            }
        }
    }

    @Test
    void rejectsMissingSessionAndMissingCsrf() throws Exception {
        SalesMember seller = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "보안고객");
        String body = quoteJson(customerId, "2026-10-29", itemJson(productId("SH-600"), 1));

        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/quotes")
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertEquals(0, quoteCount(customerId));
    }

    @Test
    void listsOnlyOwnQuotesNewestFirst() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "목록고객");
        long otherCustomerId = insertCustomer(other.id(), "다른고객");
        long shoeId = productId("SH-600");

        long oldest = createQuote(seller, customerId, "2026-10-27", itemJson(shoeId, 1));
        long middle = createQuote(seller, customerId, "2026-10-28", itemJson(shoeId, 2));
        long newest = createQuote(seller, customerId, "2026-10-29", itemJson(shoeId, 1));
        long foreign = createQuote(other, otherCustomerId, "2026-10-29", itemJson(shoeId, 1));
        jdbcTemplate.update("UPDATE quote SET created_at = ? WHERE id = ?", Timestamp.valueOf("2026-10-27 09:00:00"), oldest);
        jdbcTemplate.update("UPDATE quote SET created_at = ? WHERE id = ?", Timestamp.valueOf("2026-10-28 09:00:00"), middle);
        jdbcTemplate.update("UPDATE quote SET created_at = ? WHERE id = ?", Timestamp.valueOf("2026-10-29 09:00:00"), newest);
        jdbcTemplate.update("UPDATE quote SET created_at = ? WHERE id = ?", Timestamp.valueOf("2026-10-30 09:00:00"), foreign);

        mockMvc.perform(get("/api/quotes")
                        .session(seller.session())
                        .param("memberId", String.valueOf(other.id()))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(newest)))
                .andExpect(jsonPath("$.items[0].customerId").value(jsonId(customerId)))
                .andExpect(jsonPath("$.items[0].customerName").value("목록고객"))
                .andExpect(jsonPath("$.items[0].quoteDate").value("2026-10-29"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].supplyAmount").value(120000.00))
                .andExpect(jsonPath("$.items[0].vatAmount").value(12000))
                .andExpect(jsonPath("$.items[0].totalAmount").value(132000.00))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-29T09:00:00"))
                .andExpect(jsonPath("$.items[0].quoteNumber", startsWith("Q-20261029-")))
                .andExpect(jsonPath("$.items[0].items").doesNotExist())
                .andExpect(jsonPath("$.items[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(jsonId(middle)))
                .andExpect(jsonPath("$.items[1].quoteDate").value("2026-10-28"))
                .andExpect(jsonPath("$.items[1].supplyAmount").value(240000.00));

        mockMvc.perform(get("/api/quotes")
                        .session(seller.session())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(oldest)));

        mockMvc.perform(get("/api/quotes").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3));

        MvcResult otherList = mockMvc.perform(get("/api/quotes").session(other.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(foreign)))
                .andExpect(jsonPath("$.items[0].customerName").value("다른고객"))
                .andReturn();
        List<Number> visibleIds = JsonPath.read(
                otherList.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.items[*].id"
        );
        assertFalse(visibleIds.stream().anyMatch(id -> id.longValue() == newest));
    }

    @Test
    void readsOwnQuoteWithItemSnapshots() throws Exception {
        SalesMember seller = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "상세고객");
        long shoeId = productId("SH-600");
        long quoteId = createQuote(seller, customerId, "2026-10-29", itemJson(shoeId, 2));

        mockMvc.perform(get("/api/quotes/{id}", quoteId).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(quoteId)))
                .andExpect(jsonPath("$.customerId").value(jsonId(customerId)))
                .andExpect(jsonPath("$.customerName").value("상세고객"))
                .andExpect(jsonPath("$.quoteNumber", startsWith("Q-20261029-")))
                .andExpect(jsonPath("$.quoteDate").value("2026-10-29"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.supplyAmount").value(240000.00))
                .andExpect(jsonPath("$.vatAmount").value(24000))
                .andExpect(jsonPath("$.totalAmount").value(264000.00))
                .andExpect(jsonPath("$.memberId").doesNotExist())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId").value(jsonId(shoeId)))
                .andExpect(jsonPath("$.items[0].productName").value("화이트 신발장"))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[0].salePrice").value(120000.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].amount").value(240000.00));
    }

    @Test
    void rejectsMissingAndOtherMembersQuote() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "소유고객");
        long quoteId = createQuote(seller, customerId, "2026-10-29", itemJson(productId("SH-600"), 1));

        mockMvc.perform(get("/api/quotes/{id}", 999999999L).session(seller.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("견적서를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.quoteNumber").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());

        mockMvc.perform(get("/api/quotes/{id}", quoteId).session(other.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("본인의 견적서만 조회하거나 수정할 수 있습니다."))
                .andExpect(jsonPath("$.quoteNumber").doesNotExist())
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.supplyAmount").doesNotExist())
                .andExpect(jsonPath("$.totalAmount").doesNotExist());
    }

    @Test
    void rejectsUnauthenticatedQuoteReadsAndInvalidPage() throws Exception {
        mockMvc.perform(get("/api/quotes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/quotes/{id}", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        SalesMember seller = loginActiveSales();
        mockMvc.perform(get("/api/quotes")
                        .session(seller.session())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));
        mockMvc.perform(get("/api/quotes")
                        .session(seller.session())
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
    }

    @Test
    void updatesOwnQuoteFromCurrentProductPrices() throws Exception {
        SalesMember seller = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "기존고객");
        long nextCustomerId = insertCustomer(seller.id(), "수정고객");
        long productId = insertProduct("UPD-" + token(), "수정전제품", "100.00", "ACTIVE");
        long quoteId = createQuote(seller, customerId, "2026-10-29", itemJson(productId, 1));
        String quoteNumber = quoteNumber(quoteId);
        long previousItemId = itemId(quoteId);
        jdbcTemplate.update("UPDATE quote SET status = 'SENT' WHERE id = ?", quoteId);
        jdbcTemplate.update(
                "UPDATE product SET name = ?, sale_price = ? WHERE id = ?",
                "수정후제품",
                new BigDecimal("15.00"),
                productId
        );

        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"quoteDate":"2026-11-02","quoteNumber":"Q-HACK","status":"CANCELLED","items":[%s]}
                                """.formatted(nextCustomerId, itemJson(productId, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(quoteId)))
                .andExpect(jsonPath("$.quoteNumber").value(quoteNumber))
                .andExpect(jsonPath("$.customerId").value(jsonId(nextCustomerId)))
                .andExpect(jsonPath("$.customerName").value("수정고객"))
                .andExpect(jsonPath("$.quoteDate").value("2026-11-02"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.supplyAmount").value(15.00))
                .andExpect(jsonPath("$.vatAmount").value(2))
                .andExpect(jsonPath("$.totalAmount").value(17.00))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId").value(jsonId(productId)))
                .andExpect(jsonPath("$.items[0].productName").value("수정후제품"))
                .andExpect(jsonPath("$.items[0].productCode", startsWith("UPD-")))
                .andExpect(jsonPath("$.items[0].salePrice").value(15.00))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(15.00));

        StoredQuote stored = storedQuote(quoteId);
        assertEquals(seller.id(), stored.memberId());
        assertEquals(nextCustomerId, stored.customerId());
        assertEquals("SENT", stored.status());
        assertEquals(quoteNumber, quoteNumber(quoteId));
        assertEquals("2026-11-02", quoteDate(quoteId));
        assertEquals(0, new BigDecimal("15.00").compareTo(stored.supplyAmount()));
        assertEquals(0, new BigDecimal("2").compareTo(stored.vatAmount()));
        assertEquals(0, new BigDecimal("17.00").compareTo(stored.totalAmount()));
        assertEquals(1, itemCount(quoteId));
        assertNotEquals(previousItemId, itemId(quoteId));
        assertEquals(0, quoteCount(customerId));
    }

    @Test
    void rejectsUpdateOfAnotherMembersQuote() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "소유고객");
        long otherCustomerId = insertCustomer(other.id(), "다른고객");
        long quoteId = createQuote(seller, customerId, "2026-10-29", itemJson(productId("SH-600"), 1));
        String quoteNumber = quoteNumber(quoteId);

        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(other.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(otherCustomerId, "2026-11-02", itemJson(productId("OF-100"), 3))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("본인의 견적서만 조회하거나 수정할 수 있습니다."))
                .andExpect(jsonPath("$.quoteNumber").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.totalAmount").doesNotExist());
        assertOriginalShoeQuote(quoteId, quoteNumber, customerId);
        assertEquals(0, quoteCount(otherCustomerId));
    }

    @Test
    void rejectsMissingQuoteUpdateAndMissingCredentials() throws Exception {
        SalesMember seller = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "인증고객");
        long quoteId = createQuote(seller, customerId, "2026-10-29", itemJson(productId("SH-600"), 1));
        String quoteNumber = quoteNumber(quoteId);
        String body = quoteJson(customerId, "2026-11-02", itemJson(productId("SH-600"), 2));

        mockMvc.perform(put("/api/quotes/{id}", 999999999L)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"))
                .andExpect(jsonPath("$.quoteNumber").doesNotExist());
        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertOriginalShoeQuote(quoteId, quoteNumber, customerId);
    }

    @Test
    void rejectsInvalidQuoteUpdate() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        long customerId = insertCustomer(seller.id(), "검증고객");
        long otherCustomerId = insertCustomer(other.id(), "타인고객");
        long quoteId = createQuote(seller, customerId, "2026-10-29", itemJson(productId("SH-600"), 1));
        String quoteNumber = quoteNumber(quoteId);
        long inactiveProductId = insertProduct("OFF-" + token(), "중지제품", "1000.00", "INACTIVE");

        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(productId("SH-600"), 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("수량은 1 이상이어야 합니다."));
        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"quoteDate":"2026-10-29","items":[]}
                                """.formatted(customerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("견적 항목을 한 개 이상 입력해 주세요."));
        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(
                                customerId,
                                "2026-10-29",
                                itemJson(productId("SH-600"), 1) + "," + itemJson(productId("SH-600"), 2)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT"));
        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(999999999L, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, "2026-10-29", itemJson(inactiveProductId, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ACTIVE"));
        mockMvc.perform(put("/api/quotes/{id}", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(otherCustomerId, "2026-10-29", itemJson(productId("SH-600"), 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ACCESS_DENIED"));
        assertOriginalShoeQuote(quoteId, quoteNumber, customerId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackQuoteUpdateWhenItemInsertFails() throws Exception {
        SalesMember seller = null;
        Long customerId = null;
        Long nextCustomerId = null;
        try {
            seller = loginActiveSales();
            customerId = insertCustomer(seller.id(), "롤백고객");
            nextCustomerId = insertCustomer(seller.id(), "롤백변경고객");
            long quoteId = createQuote(seller, customerId, "2026-10-29", itemJson(productId("SH-600"), 1));
            String quoteNumber = quoteNumber(quoteId);
            doThrow(new DataIntegrityViolationException("quote item insert failed"))
                    .when(quoteRepository)
                    .insertItems(anyLong(), anyList());

            mockMvc.perform(put("/api/quotes/{id}", quoteId)
                            .with(csrf())
                            .session(seller.session())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(quoteJson(nextCustomerId, "2026-11-02", itemJson(productId("OF-100"), 4))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("DATA_ACCESS_ERROR"));
            assertOriginalShoeQuote(quoteId, quoteNumber, customerId);
            assertEquals(0, quoteCount(nextCustomerId));
        } finally {
            deleteCustomer(customerId);
            deleteCustomer(nextCustomerId);
            if (seller != null) {
                jdbcTemplate.update("DELETE FROM member WHERE id = ?", seller.id());
            }
        }
    }

    private long createQuote(SalesMember seller, long customerId, String quoteDate, String items) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, quoteDate, items)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private SalesMember loginActiveSales() throws Exception {
        String email = "member-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"SALES","name":"견적회원","email":"%s","password":"password1234","phone":"01012345678","companyName":"테스트회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING' AND email = ?",
                id.longValue(),
                email
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
        return new SalesMember(id.longValue(), (MockHttpSession) login.getRequest().getSession(false));
    }

    private long insertCustomer(long memberId, String name) {
        String sql = """
                INSERT INTO customer (member_id, name, customer_type, status)
                VALUES (?, ?, 'COMPANY', 'ACTIVE')
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, memberId);
            statement.setString(2, name);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("customer id was not generated");
        }
        return key.longValue();
    }

    private long insertProduct(String code, String name, String salePrice, String status) {
        String sql = """
                INSERT INTO product (category_id, product_code, name, unit_price, sale_price, status)
                VALUES (1, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, code);
            statement.setString(2, name);
            statement.setBigDecimal(3, new BigDecimal(salePrice));
            statement.setBigDecimal(4, new BigDecimal(salePrice));
            statement.setString(5, status);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("product id was not generated");
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

    private void assertOriginalShoeQuote(long quoteId, String quoteNumber, long customerId) {
        StoredQuote stored = storedQuote(quoteId);
        assertEquals(customerId, stored.customerId());
        assertEquals("DRAFT", stored.status());
        assertEquals(0, new BigDecimal("120000.00").compareTo(stored.supplyAmount()));
        assertEquals(0, new BigDecimal("12000").compareTo(stored.vatAmount()));
        assertEquals(0, new BigDecimal("132000.00").compareTo(stored.totalAmount()));
        assertEquals(quoteNumber, quoteNumber(quoteId));
        assertEquals("2026-10-29", quoteDate(quoteId));
        assertEquals(1, itemCount(quoteId));
        assertEquals(productId("SH-600"), storedItemProductId(quoteId));
    }

    private void deleteCustomer(Long customerId) {
        if (customerId == null) {
            return;
        }
        jdbcTemplate.update("""
                DELETE quote_item FROM quote_item
                INNER JOIN quote ON quote.id = quote_item.quote_id
                WHERE quote.customer_id = ?
                """, customerId);
        jdbcTemplate.update("DELETE FROM quote WHERE customer_id = ?", customerId);
        jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customerId);
    }

    private String quoteNumber(long quoteId) {
        String quoteNumber = jdbcTemplate.queryForObject(
                "SELECT quote_number FROM quote WHERE id = ?",
                String.class,
                quoteId
        );
        if (quoteNumber == null) {
            throw new IllegalStateException("quote number was not stored");
        }
        return quoteNumber;
    }

    private String quoteDate(long quoteId) {
        String quoteDate = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(quote_date, '%Y-%m-%d') FROM quote WHERE id = ?",
                String.class,
                quoteId
        );
        if (quoteDate == null) {
            throw new IllegalStateException("quote date was not stored");
        }
        return quoteDate;
    }

    private int itemCount(long quoteId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quote_item WHERE quote_id = ?",
                Integer.class,
                quoteId
        );
        return count == null ? 0 : count;
    }

    private long itemId(long quoteId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM quote_item WHERE quote_id = ? ORDER BY id LIMIT 1",
                Long.class,
                quoteId
        );
        if (id == null) {
            throw new IllegalStateException("quote item was not stored");
        }
        return id;
    }

    private int quoteCount(long customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quote WHERE customer_id = ?",
                Integer.class,
                customerId
        );
        return count == null ? 0 : count;
    }

    private StoredQuote storedQuote(long quoteId) {
        return jdbcTemplate.queryForObject("""
                SELECT member_id, customer_id, supply_amount, vat_amount, total_amount, status
                FROM quote
                WHERE id = ?
                """, (rs, rowNum) -> new StoredQuote(
                rs.getLong("member_id"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("supply_amount"),
                rs.getBigDecimal("vat_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status")
        ), quoteId);
    }

    private long storedItemProductId(long quoteId) {
        Long productId = jdbcTemplate.queryForObject(
                "SELECT product_id FROM quote_item WHERE quote_id = ? ORDER BY id LIMIT 1",
                Long.class,
                quoteId
        );
        if (productId == null) {
            throw new IllegalStateException("quote item was not stored");
        }
        return productId;
    }

    private static String quoteJson(long customerId, String quoteDate, String items) {
        return """
                {"customerId":%d,"quoteDate":"%s","items":[%s]}
                """.formatted(customerId, quoteDate, items);
    }

    private static String itemJson(long productId, int quantity) {
        return """
                {"productId":%d,"quantity":%d}
                """.formatted(productId, quantity);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static int jsonId(long id) {
        return Math.toIntExact(id);
    }

    private record SalesMember(long id, MockHttpSession session) {
    }

    private record StoredQuote(
            long memberId,
            long customerId,
            BigDecimal supplyAmount,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            String status
    ) {
    }
}
