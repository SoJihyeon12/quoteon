package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminQuoteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminListsQuotesFromEverySalesMember() throws Exception {
        SalesMember first = loginSales("김영업");
        SalesMember second = loginSales("이상담");
        long firstCustomerId = insertCustomer(first.id(), "김진우", null, null, null, null);
        long secondCustomerId = insertCustomer(second.id(), "오민서", null, null, null, null);
        long earlier = createQuote(second, secondCustomerId, "2026-10-17", itemJson(productId("SH-800"), 1));
        long later = createQuote(first, firstCustomerId, "2026-10-18", itemJson(productId("SH-600"), 1));
        long sameDay = createQuote(first, firstCustomerId, "2026-10-18", itemJson(productId("OF-100"), 1));
        jdbcTemplate.update(
                "UPDATE quote SET created_at = ? WHERE id = ?",
                Timestamp.valueOf("2020-01-01 00:00:00"),
                sameDay
        );

        mockMvc.perform(get("/api/admin/quotes").session(loginAdmin().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(sameDay)))
                .andExpect(jsonPath("$.items[0].quoteDate").value("2026-10-18"))
                .andExpect(jsonPath("$.items[0].authorName").value("김영업"))
                .andExpect(jsonPath("$.items[0].customerName").value("김진우"))
                .andExpect(jsonPath("$.items[0].totalAmount").value(148500.00))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].isPrivate").value(false))
                .andExpect(jsonPath("$.items[0].supplyAmount").doesNotExist())
                .andExpect(jsonPath("$.items[0].quoteNumber").doesNotExist())
                .andExpect(jsonPath("$.items[0].items").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(jsonId(later)))
                .andExpect(jsonPath("$.items[1].authorName").value("김영업"))
                .andExpect(jsonPath("$.items[1].customerName").value("김진우"))
                .andExpect(jsonPath("$.items[1].totalAmount").value(132000.00))
                .andExpect(jsonPath("$.items[1].isPrivate").value(false))
                .andExpect(jsonPath("$.items[2].id").value(jsonId(earlier)))
                .andExpect(jsonPath("$.items[2].quoteDate").value("2026-10-17"))
                .andExpect(jsonPath("$.items[2].authorName").value("이상담"))
                .andExpect(jsonPath("$.items[2].customerName").value("오민서"))
                .andExpect(jsonPath("$.items[2].totalAmount").value(165000.00))
                .andExpect(jsonPath("$.items[2].isPrivate").value(false));
    }

    @Test
    void adminReadsAnotherSalesMembersQuote() throws Exception {
        SalesMember seller = loginSales("김영업");
        long customerId = insertCustomer(
                seller.id(),
                "김진우",
                "01012345678",
                "customer@quoteon.test",
                "서울시 강남구",
                "고객 메모"
        );
        long quoteId = createQuote(seller, customerId, "2026-10-18", itemJson(productId("SH-600"), 2));

        mockMvc.perform(get("/api/admin/quotes/{quoteId}", quoteId).session(loginAdmin().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(quoteId)))
                .andExpect(jsonPath("$.quoteNumber", startsWith("Q-20261018-")))
                .andExpect(jsonPath("$.quoteDate").value("2026-10-18"))
                .andExpect(jsonPath("$.authorName").value("김영업"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.isPrivate").value(false))
                .andExpect(jsonPath("$.supplyAmount").value(240000.00))
                .andExpect(jsonPath("$.vatAmount").value(24000))
                .andExpect(jsonPath("$.totalAmount").value(264000.00))
                .andExpect(jsonPath("$.customer.id").value(jsonId(customerId)))
                .andExpect(jsonPath("$.customer.name").value("김진우"))
                .andExpect(jsonPath("$.customer.customerType").value("COMPANY"))
                .andExpect(jsonPath("$.customer.phone").value("01012345678"))
                .andExpect(jsonPath("$.customer.email").value("customer@quoteon.test"))
                .andExpect(jsonPath("$.customer.address").value("서울시 강남구"))
                .andExpect(jsonPath("$.customer.memo").doesNotExist())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("화이트 신발장"))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[0].salePrice").value(120000.00))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].amount").value(240000.00))
                .andExpect(jsonPath("$.items[0].productId").doesNotExist())
                .andExpect(jsonPath("$.items[0].specification").value("600x330x1828mm"))
                .andExpect(jsonPath("$.items[0].specificationSource").value("SNAPSHOT"))
                .andExpect(jsonPath("$.memo").value(nullValue()))
                .andExpect(jsonPath("$.paymentMethod").doesNotExist())
                .andExpect(jsonPath("$.supplier").doesNotExist())
                .andExpect(jsonPath("$.manufacturer").doesNotExist())
                .andExpect(jsonPath("$.validDays").doesNotExist());
    }

    @Test
    void adminCanHideAndRestoreQuotePrivacyForSalesAccess() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(
                seller,
                insertCustomer(seller.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );
        MockHttpSession admin = loginAdmin().session();

        mockMvc.perform(get("/api/quotes").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(quoteId)));
        mockMvc.perform(get("/api/quotes/{id}", quoteId).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(quoteId)));

        mockMvc.perform(patch("/api/admin/quotes/{quoteId}/privacy", quoteId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(quoteId)))
                .andExpect(jsonPath("$.isPrivate").value(true));

        mockMvc.perform(get("/api/admin/quotes/{quoteId}", quoteId).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(true));
        mockMvc.perform(get("/api/admin/quotes").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].isPrivate").value(true));

        mockMvc.perform(get("/api/quotes").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/quotes/{id}", quoteId).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_PRIVATE"));
        mockMvc.perform(get("/api/quotes/{id}/pdf", quoteId).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_PRIVATE"));
        mockMvc.perform(get("/api/quotes/{id}/jpeg", quoteId).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_PRIVATE"));
        mockMvc.perform(get("/api/quotes/{id}/png", quoteId).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_PRIVATE"));
        mockMvc.perform(post("/api/quotes/{id}/email", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"buyer@quoteon.test","subject":"견적서","content":"첨부합니다."}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_PRIVATE"));
        mockMvc.perform(get("/api/quotes/{id}/email/histories", quoteId).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_PRIVATE"));
        mockMvc.perform(post("/api/quotes/{id}/email/histories/1/retry", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"buyer@quoteon.test","subject":"견적서","content":"첨부합니다."}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_PRIVATE"));

        mockMvc.perform(patch("/api/admin/quotes/{quoteId}/privacy", quoteId)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/admin/quotes/{quoteId}/privacy", quoteId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPrivate\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(false));

        mockMvc.perform(get("/api/quotes").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(quoteId)));
        mockMvc.perform(get("/api/quotes/{id}", quoteId).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(quoteId)));
        mockMvc.perform(get("/api/quotes/{id}/pdf", quoteId).session(seller.session()))
                .andExpect(status().isOk());
    }

    @Test
    void salesCannotAccessAdminQuotes() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(
                seller,
                insertCustomer(seller.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );

        mockMvc.perform(get("/api/admin/quotes").session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("이 기능을 사용할 권한이 없습니다."));
        mockMvc.perform(get("/api/admin/quotes/{quoteId}", quoteId).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.quoteNumber").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
        mockMvc.perform(delete("/api/admin/quotes/{quoteId}", quoteId).with(csrf()).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminDeletesQuoteAndKeepsRelatedRowsRemoved() throws Exception {
        SalesMember seller = loginSales("김영업");
        SalesMember other = loginSales("이상담");
        long deletedId = createQuote(
                seller,
                insertCustomer(seller.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );
        long keptId = createQuote(
                other,
                insertCustomer(other.id(), "오민서", null, null, null, null),
                "2026-10-19",
                itemJson(productId("SH-800"), 1)
        );
        jdbcTemplate.update(
                """
                INSERT INTO quote_email_history (quote_id, recipient_email, subject, status, failure_reason)
                VALUES (?, 'customer@quoteon.test', '견적서', 'FAILED', '연결 실패')
                """,
                deletedId
        );
        Admin admin = loginAdmin();

        mockMvc.perform(delete("/api/admin/quotes/{quoteId}", deletedId).session(admin.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        mockMvc.perform(delete("/api/admin/quotes/{quoteId}", 999999999L).with(csrf()).session(admin.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        mockMvc.perform(delete("/api/admin/quotes/{quoteId}", deletedId).with(csrf()).session(admin.session()))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quote WHERE id = ?", Integer.class, deletedId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quote_item WHERE quote_id = ?", Integer.class, deletedId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quote_email_history WHERE quote_id = ?", Integer.class, deletedId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quote WHERE id = ?", Integer.class, keptId));
        mockMvc.perform(get("/api/admin/quotes/{quoteId}", deletedId).session(admin.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/quotes").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/quotes").session(other.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(keptId)));
    }

    @Test
    void unauthenticatedCannotAccessAdminQuotes() throws Exception {
        mockMvc.perform(get("/api/admin/quotes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        mockMvc.perform(get("/api/admin/quotes/{quoteId}", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void missingQuoteIsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/quotes/{quoteId}", 999999999L).session(loginAdmin().session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("견적서를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.quoteNumber").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void filtersByInclusiveQuoteDateRange() throws Exception {
        SalesMember seller = loginSales("김영업");
        long customerId = insertCustomer(seller.id(), "김진우", null, null, null, null);
        long on16 = createQuote(seller, customerId, "2026-10-16", itemJson(productId("SH-600"), 1));
        long on17 = createQuote(seller, customerId, "2026-10-17", itemJson(productId("SH-600"), 1));
        long on18 = createQuote(seller, customerId, "2026-10-18", itemJson(productId("SH-600"), 1));
        MockHttpSession admin = loginAdmin().session();

        mockMvc.perform(get("/api/admin/quotes")
                        .session(admin)
                        .param("quoteDateFrom", "2026-10-17")
                        .param("quoteDateTo", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(on18)))
                .andExpect(jsonPath("$.items[1].id").value(jsonId(on17)));
        mockMvc.perform(get("/api/admin/quotes")
                        .session(admin)
                        .param("quoteDateFrom", "2026-10-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(on18)));
        mockMvc.perform(get("/api/admin/quotes")
                        .session(admin)
                        .param("quoteDateTo", "2026-10-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(on16)));
        mockMvc.perform(get("/api/admin/quotes")
                        .session(admin)
                        .param("quoteDateFrom", "2026-10-18")
                        .param("quoteDateTo", "2026-10-17"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("작성일 시작일은 종료일보다 늦을 수 없습니다."));
        mockMvc.perform(get("/api/admin/quotes")
                        .session(admin)
                        .param("quoteDateFrom", "2026/10/18"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("작성일 시작일 형식을 확인해 주세요."));
    }

    @Test
    void filtersByAuthorName() throws Exception {
        SalesMember kim = loginSales("김영업");
        SalesMember lee = loginSales("이상담");
        long kimQuote = createQuote(
                kim,
                insertCustomer(kim.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );
        createQuote(
                lee,
                insertCustomer(lee.id(), "오민서", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );
        MockHttpSession admin = loginAdmin().session();

        mockMvc.perform(get("/api/admin/quotes").session(admin).param("authorName", "영업"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(kimQuote)))
                .andExpect(jsonPath("$.items[0].authorName").value("김영업"));
        mockMvc.perform(get("/api/admin/quotes").session(admin).param("authorName", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void filtersByCustomerName() throws Exception {
        SalesMember seller = loginSales("김영업");
        long matched = createQuote(
                seller,
                insertCustomer(seller.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );
        createQuote(
                seller,
                insertCustomer(seller.id(), "오민서", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );

        mockMvc.perform(get("/api/admin/quotes")
                        .session(loginAdmin().session())
                        .param("customerName", "진우"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(matched)))
                .andExpect(jsonPath("$.items[0].customerName").value("김진우"));
    }

    @Test
    void filtersByProductNameOrCodeSnapshot() throws Exception {
        SalesMember seller = loginSales("김영업");
        long customerId = insertCustomer(seller.id(), "김진우", null, null, null, null);
        long shoeId = productId("SH-600");
        long quoteId = createQuote(seller, customerId, "2026-10-18", itemJson(shoeId, 1));
        jdbcTemplate.update(
                "UPDATE product SET name = ?, product_code = ? WHERE id = ?",
                "변경된 제품",
                "NEW-" + token(),
                shoeId
        );
        MockHttpSession admin = loginAdmin().session();

        mockMvc.perform(get("/api/admin/quotes").session(admin).param("productKeyword", "신발"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(quoteId)));
        mockMvc.perform(get("/api/admin/quotes").session(admin).param("productKeyword", "SH-600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(quoteId)));
        mockMvc.perform(get("/api/admin/quotes").session(admin).param("productKeyword", "변경된"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/admin/quotes").session(admin).param("productKeyword", "NEW-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByDraftAndSent() throws Exception {
        SalesMember seller = loginSales("김영업");
        long customerId = insertCustomer(seller.id(), "김진우", null, null, null, null);
        long draftId = createQuote(seller, customerId, "2026-10-18", itemJson(productId("SH-600"), 1));
        long sentId = createQuote(seller, customerId, "2026-10-17", itemJson(productId("SH-800"), 1));
        jdbcTemplate.update("UPDATE quote SET status = 'SENT' WHERE id = ?", sentId);
        MockHttpSession admin = loginAdmin().session();

        mockMvc.perform(get("/api/admin/quotes").session(admin).param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(draftId)))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"));
        mockMvc.perform(get("/api/admin/quotes").session(admin).param("status", "SENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(sentId)))
                .andExpect(jsonPath("$.items[0].status").value("SENT"));
        mockMvc.perform(get("/api/admin/quotes").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/admin/quotes").session(admin).param("status", "비공개"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("견적 상태는 DRAFT 또는 SENT만 사용할 수 있습니다."));
    }

    @Test
    void combinesSearchConditions() throws Exception {
        SalesMember kim = loginSales("김영업");
        SalesMember lee = loginSales("이상담");
        long matched = createQuote(
                kim,
                insertCustomer(kim.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );
        createQuote(
                lee,
                insertCustomer(lee.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 1)
        );
        createQuote(
                kim,
                insertCustomer(kim.id(), "오민서", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-800"), 1)
        );
        MockHttpSession admin = loginAdmin().session();

        mockMvc.perform(get("/api/admin/quotes")
                        .session(admin)
                        .param("quoteDateFrom", "2026-10-18")
                        .param("quoteDateTo", "2026-10-18")
                        .param("authorName", "김영업")
                        .param("customerName", "진우")
                        .param("productKeyword", "SH-600")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(matched)));
        mockMvc.perform(get("/api/admin/quotes")
                        .session(admin)
                        .param("quoteDateFrom", "2026-10-18")
                        .param("quoteDateTo", "2026-10-18")
                        .param("authorName", "김영업")
                        .param("customerName", "진우")
                        .param("productKeyword", "SH-600")
                        .param("status", "SENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void doesNotDuplicateQuoteWithMultipleMatchingItems() throws Exception {
        SalesMember seller = loginSales("김영업");
        long customerId = insertCustomer(seller.id(), "김진우", null, null, null, null);
        long matched = createQuote(
                seller,
                customerId,
                "2026-10-18",
                itemJson(productId("SH-600"), 1) + "," + itemJson(productId("SH-800"), 1)
        );
        createQuote(seller, customerId, "2026-10-17", itemJson(productId("OF-100"), 1));

        mockMvc.perform(get("/api/admin/quotes")
                        .session(loginAdmin().session())
                        .param("productKeyword", "SH-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(matched)));
    }

    @Test
    void listAmountMatchesTotalAmount() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(
                seller,
                insertCustomer(seller.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(productId("SH-600"), 2)
        );
        BigDecimal storedTotal = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM quote WHERE id = ?",
                BigDecimal.class,
                quoteId
        );

        MvcResult result = mockMvc.perform(get("/api/admin/quotes").session(loginAdmin().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(quoteId)))
                .andExpect(jsonPath("$.items[0].totalAmount").value(264000.00))
                .andExpect(jsonPath("$.items[0].supplyAmount").doesNotExist())
                .andExpect(jsonPath("$.items[0].vatAmount").doesNotExist())
                .andReturn();
        Number responseTotal = JsonPath.read(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.items[0].totalAmount"
        );
        assertEquals(0, storedTotal.compareTo(new BigDecimal(responseTotal.toString())));
    }

    @Test
    void detailKeepsItemSnapshotAfterProductChange() throws Exception {
        SalesMember seller = loginSales("김영업");
        long shoeId = productId("SH-600");
        long quoteId = createQuote(
                seller,
                insertCustomer(seller.id(), "김진우", null, null, null, null),
                "2026-10-18",
                itemJson(shoeId, 1)
        );
        jdbcTemplate.update(
                "UPDATE product SET name = ?, product_code = ?, sale_price = ?, specification = ? WHERE id = ?",
                "변경된 제품",
                "NEW-" + token(),
                new BigDecimal("1.00"),
                "변경된 규격",
                shoeId
        );

        mockMvc.perform(get("/api/admin/quotes/{quoteId}", quoteId).session(loginAdmin().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("화이트 신발장"))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[0].salePrice").value(120000.00))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(120000.00))
                .andExpect(jsonPath("$.items[0].specification").value("600x330x1828mm"))
                .andExpect(jsonPath("$.items[0].specificationSource").value("SNAPSHOT"));
    }

    @Test
    void salesQuoteApisStayLimitedToTheAuthor() throws Exception {
        SalesMember seller = loginSales("김영업");
        SalesMember other = loginSales("이상담");
        long sellerCustomerId = insertCustomer(seller.id(), "김진우", null, null, null, null);
        long otherCustomerId = insertCustomer(other.id(), "오민서", null, null, null, null);
        long ownQuoteId = createQuote(seller, sellerCustomerId, "2026-10-18", itemJson(productId("SH-600"), 1));
        long otherQuoteId = createQuote(other, otherCustomerId, "2026-10-18", itemJson(productId("SH-800"), 1));

        MvcResult ownList = mockMvc.perform(get("/api/quotes").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(ownQuoteId)))
                .andExpect(jsonPath("$.items[0].customerName").value("김진우"))
                .andExpect(jsonPath("$.items[0].supplyAmount").value(120000.00))
                .andExpect(jsonPath("$.items[0].authorName").doesNotExist())
                .andReturn();
        List<Number> visibleIds = JsonPath.read(
                ownList.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.items[*].id"
        );
        assertFalse(visibleIds.stream().anyMatch(id -> id.longValue() == otherQuoteId));

        mockMvc.perform(get("/api/quotes/{id}", ownQuoteId).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(jsonId(sellerCustomerId)))
                .andExpect(jsonPath("$.items[0].productId").value(jsonId(productId("SH-600"))))
                .andExpect(jsonPath("$.items[0].productName").value("화이트 신발장"))
                .andExpect(jsonPath("$.authorName").doesNotExist());
        mockMvc.perform(get("/api/quotes/{id}", otherQuoteId).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("QUOTE_ACCESS_DENIED"));

        mockMvc.perform(get("/api/admin/quotes").session(loginAdmin().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        MockHttpSession admin = loginAdmin().session();
        mockMvc.perform(get("/api/admin/quotes").session(admin).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));
        mockMvc.perform(get("/api/admin/quotes").session(admin).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
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

    private Admin loginAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"ADMIN","name":"견적관리자","email":"%s","password":"password1234","phone":"01012345678","companyName":"관리회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idOf(signup);
        activate(id);
        return new Admin(id, login(email));
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

    private long insertCustomer(
            long memberId,
            String name,
            String phone,
            String email,
            String address,
            String memo
    ) {
        String sql = """
                INSERT INTO customer (member_id, name, customer_type, phone, email, address, memo, status)
                VALUES (?, ?, 'COMPANY', ?, ?, ?, ?, 'ACTIVE')
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, memberId);
            statement.setString(2, name);
            statement.setString(3, phone);
            statement.setString(4, email);
            statement.setString(5, address);
            statement.setString(6, memo);
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

    private static String quoteJson(long customerId, String quoteDate, String items) {
        return """
                {"customerId":%d,"quoteDate":"%s","paymentMethod":"CASH","items":[%s]}
                """.formatted(customerId, quoteDate, items);
    }

    private static String itemJson(long productId, int quantity) {
        return """
                {"productId":%d,"quantity":%d}
                """.formatted(productId, quantity);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static int jsonId(long id) {
        return Math.toIntExact(id);
    }

    private record SalesMember(long id, MockHttpSession session) {
    }

    private record Admin(long id, MockHttpSession session) {
    }
}
