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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminCanReadDashboardAndGuestsCannot() throws Exception {
        MockHttpSession admin = login("ADMIN", "대시보드관리자").session();
        mockMvc.perform(get("/api/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayQuoteCount").exists())
                .andExpect(jsonPath("$.monthQuoteAmount").exists())
                .andExpect(jsonPath("$.pendingMemberCount").exists())
                .andExpect(jsonPath("$.activeMemberCount").exists())
                .andExpect(jsonPath("$.popularProducts").isArray())
                .andExpect(jsonPath("$.memberQuoteRanks").isArray());

        MockHttpSession sales = login("SALES", "영업사원").session();
        mockMvc.perform(get("/api/admin/dashboard").session(sales))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aggregatesQuotesBySeoulCreatedAtAndTotalAmount() throws Exception {
        MockHttpSession admin = login("ADMIN", "금액관리자").session();
        long memberId = insertMember("금액회원", "ROLE_USER", "ACTIVE");
        long customerId = insertCustomer(memberId);
        LocalDateTime todayStart = LocalDate.now(SEOUL).atStartOfDay();
        LocalDateTime monthStart = todayStart.withDayOfMonth(1);
        insertQuote(memberId, customerId, "DRAFT", "10.00", todayStart);
        insertQuote(memberId, customerId, "SENT", "20.50", todayStart.plusMinutes(1));
        insertQuote(memberId, customerId, "DRAFT", "5.00", todayStart.minusSeconds(1));
        insertQuote(memberId, customerId, "SENT", "1000.00", monthStart.minusSeconds(1));

        BigDecimal monthAmount = new BigDecimal("30.50");
        if (!todayStart.minusSeconds(1).isBefore(monthStart)) {
            monthAmount = monthAmount.add(new BigDecimal("5.00"));
        }
        MvcResult result = mockMvc.perform(get("/api/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayQuoteCount").value(2))
                .andReturn();
        Number amount = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.monthQuoteAmount");
        assertEquals(0, monthAmount.compareTo(new BigDecimal(amount.toString())));
    }

    @Test
    void countsPendingMembersAndActiveSalesUsers() throws Exception {
        MockHttpSession admin = login("ADMIN", "회원수관리자").session();
        insertMember("대기영업", "ROLE_USER", "PENDING");
        insertMember("대기관리", "ROLE_ADMIN", "PENDING");
        insertMember("활성영업", "ROLE_USER", "ACTIVE");
        insertMember("활성관리", "ROLE_ADMIN", "ACTIVE");
        insertMember("중지영업", "ROLE_USER", "INACTIVE");
        insertMember("거절영업", "ROLE_USER", "REJECTED");

        mockMvc.perform(get("/api/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingMemberCount").value(2))
                .andExpect(jsonPath("$.activeMemberCount").value(1));
    }

    @Test
    void ranksTheTopFiveProductsAgainstEveryProductQuoteCount() throws Exception {
        MockHttpSession admin = login("ADMIN", "제품관리자").session();
        long memberId = insertMember("제품회원", "ROLE_USER", "ACTIVE");
        long customerId = insertCustomer(memberId);
        LocalDateTime createdAt = LocalDate.now(SEOUL).withDayOfMonth(1).atStartOfDay().plusHours(1);
        for (int count = 6; count >= 1; count--) {
            for (int quote = 0; quote < count; quote++) {
                insertItem(
                        insertQuote(memberId, customerId, "DRAFT", "1.00", createdAt),
                        "P" + count,
                        "제품" + count
                );
            }
        }
        mockMvc.perform(get("/api/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.popularProducts", hasSize(5)))
                .andExpect(jsonPath("$.popularProducts[0].productCode").value("P6"))
                .andExpect(jsonPath("$.popularProducts[0].quoteCount").value(6))
                .andExpect(jsonPath("$.popularProducts[0].ratio").value(28.6))
                .andExpect(jsonPath("$.popularProducts[1].productCode").value("P5"))
                .andExpect(jsonPath("$.popularProducts[1].quoteCount").value(5))
                .andExpect(jsonPath("$.popularProducts[1].ratio").value(23.8))
                .andExpect(jsonPath("$.popularProducts[2].productCode").value("P4"))
                .andExpect(jsonPath("$.popularProducts[3].productCode").value("P3"))
                .andExpect(jsonPath("$.popularProducts[4].productCode").value("P2"))
                .andExpect(jsonPath("$.popularProducts[4].quoteCount").value(2));
    }

    @Test
    void popularProductUsesSnapshotCodeAfterProductCodeChanges() throws Exception {
        MockHttpSession admin = login("ADMIN", "스냅샷관리자").session();
        long memberId = insertMember("스냅샷회원", "ROLE_USER", "ACTIVE");
        long customerId = insertCustomer(memberId);
        long shoeId = productId("SH-600");
        LocalDateTime createdAt = LocalDate.now(SEOUL).atStartOfDay().plusHours(4);
        insertItem(insertQuote(memberId, customerId, "DRAFT", "1.00", createdAt), shoeId, "SH-600", "화이트 신발장");
        String changedCode = "CHANGED-" + token();
        jdbcTemplate.update("UPDATE product SET product_code = ? WHERE id = ?", changedCode, shoeId);

        MvcResult result = mockMvc.perform(get("/api/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.popularProducts", hasSize(1)))
                .andExpect(jsonPath("$.popularProducts[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.popularProducts[0].quoteCount").value(1))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(changedCode));
    }

    @Test
    void countsAQuoteOnceWhenTheSameProductIsRepeated() throws Exception {
        MockHttpSession admin = login("ADMIN", "중복관리자").session();
        long memberId = insertMember("중복회원", "ROLE_USER", "ACTIVE");
        long customerId = insertCustomer(memberId);
        LocalDateTime createdAt = LocalDate.now(SEOUL).atStartOfDay().plusHours(2);
        long quoteId = insertQuote(memberId, customerId, "DRAFT", "10.00", createdAt);
        insertItem(quoteId, "DUP", "중복제품");
        insertItem(quoteId, "DUP", "중복제품");
        insertItem(insertQuote(memberId, customerId, "SENT", "10.00", createdAt), "DUP", "중복제품");
        insertItem(insertQuote(memberId, customerId, "DRAFT", "10.00", createdAt), "ONCE", "한제품");

        mockMvc.perform(get("/api/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.popularProducts", hasSize(2)))
                .andExpect(jsonPath("$.popularProducts[0].productCode").value("DUP"))
                .andExpect(jsonPath("$.popularProducts[0].quoteCount").value(2))
                .andExpect(jsonPath("$.popularProducts[0].ratio").value(66.7))
                .andExpect(jsonPath("$.popularProducts[1].productCode").value("ONCE"))
                .andExpect(jsonPath("$.popularProducts[1].quoteCount").value(1))
                .andExpect(jsonPath("$.popularProducts[1].ratio").value(33.3));
    }

    @Test
    void ranksSalesMembersWithQuotesAndKeepsTiesByMemberId() throws Exception {
        MockHttpSession admin = login("ADMIN", "순위관리자").session();
        long tieLowId = insertMember("동점가", "ROLE_USER", "ACTIVE");
        long tieHighId = insertMember("동점나", "ROLE_USER", "ACTIVE");
        long leaderId = insertMember("일등", "ROLE_USER", "ACTIVE");
        long inactiveId = insertMember("휴면", "ROLE_USER", "INACTIVE");
        long idleId = insertMember("무실적", "ROLE_USER", "ACTIVE");
        long adminAuthorId = insertMember("실적관리자", "ROLE_ADMIN", "ACTIVE");
        LocalDateTime monthStart = LocalDate.now(SEOUL).withDayOfMonth(1).atStartOfDay();
        LocalDateTime createdAt = monthStart.plusHours(3);
        long tieLowCustomerId = insertCustomer(tieLowId);
        long tieHighCustomerId = insertCustomer(tieHighId);
        long leaderCustomerId = insertCustomer(leaderId);
        insertQuote(tieLowId, tieLowCustomerId, "DRAFT", "10.00", createdAt);
        insertQuote(tieLowId, tieLowCustomerId, "SENT", "10.00", createdAt);
        insertQuote(tieHighId, tieHighCustomerId, "DRAFT", "10.00", createdAt);
        insertQuote(tieHighId, tieHighCustomerId, "SENT", "10.00", createdAt);
        insertQuote(leaderId, leaderCustomerId, "DRAFT", "10.00", createdAt);
        insertQuote(leaderId, leaderCustomerId, "DRAFT", "10.00", createdAt);
        insertQuote(leaderId, leaderCustomerId, "SENT", "10.00", createdAt);
        insertQuote(leaderId, leaderCustomerId, "SENT", "999.00", monthStart.minusSeconds(1));
        insertQuote(inactiveId, insertCustomer(inactiveId), "DRAFT", "10.00", createdAt);
        insertQuote(adminAuthorId, insertCustomer(adminAuthorId), "DRAFT", "10.00", createdAt);
        insertQuote(adminAuthorId, insertCustomer(adminAuthorId), "DRAFT", "10.00", createdAt);

        mockMvc.perform(get("/api/admin/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberQuoteRanks", hasSize(4)))
                .andExpect(jsonPath("$.memberQuoteRanks[0].memberId").value(jsonId(leaderId)))
                .andExpect(jsonPath("$.memberQuoteRanks[0].memberName").value("일등"))
                .andExpect(jsonPath("$.memberQuoteRanks[0].quoteCount").value(3))
                .andExpect(jsonPath("$.memberQuoteRanks[1].memberId").value(jsonId(tieLowId)))
                .andExpect(jsonPath("$.memberQuoteRanks[1].memberName").value("동점가"))
                .andExpect(jsonPath("$.memberQuoteRanks[1].quoteCount").value(2))
                .andExpect(jsonPath("$.memberQuoteRanks[2].memberId").value(jsonId(tieHighId)))
                .andExpect(jsonPath("$.memberQuoteRanks[2].quoteCount").value(2))
                .andExpect(jsonPath("$.memberQuoteRanks[3].memberId").value(jsonId(inactiveId)))
                .andExpect(jsonPath("$.memberQuoteRanks[3].memberName").value("휴면"))
                .andExpect(jsonPath("$.memberQuoteRanks[3].quoteCount").value(1))
                .andExpect(jsonPath("$.memberQuoteRanks[?(@.memberId == " + idleId + ")]").isEmpty())
                .andExpect(jsonPath("$.memberQuoteRanks[?(@.memberName == '실적관리자')]").isEmpty());
    }

    @Test
    void dashboardReadDoesNotChangeExistingAdminApis() throws Exception {
        Account admin = login("ADMIN", "회귀관리자");
        Account sales = login("SALES", "회귀영업");
        long customerId = insertCustomer(sales.id());
        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(sales.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"quoteDate":"2026-10-18","paymentMethod":"CASH","items":[{"productId":%d,"quantity":1}]}
                                """.formatted(customerId, productId("SH-600"))))
                .andExpect(status().isCreated());
        int quotesBefore = count("quote");

        mockMvc.perform(get("/api/admin/dashboard").session(admin.session()))
                .andExpect(status().isOk());
        assertEquals(quotesBefore, count("quote"));
        mockMvc.perform(get("/api/admin/members").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/admin/products").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/admin/quotes").session(admin.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/quotes").session(sales.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].customerName").value("회귀고객"));
    }

    private Account login(String accountType, String name) throws Exception {
        String email = accountType.toLowerCase() + "-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"%s","name":"%s","email":"%s","password":"password1234","phone":"01012345678","companyName":"테스트회사"}
                                """.formatted(accountType, name, email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idOf(signup);
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'",
                id
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
        return new Account(id, (MockHttpSession) login.getRequest().getSession(false));
    }

    private long insertMember(String name, String role, String status) {
        String sql = """
                INSERT INTO member (email, password, name, phone, company_name, role, status)
                VALUES (?, 'hash', ?, '01012345678', '테스트회사', ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name + "-" + UUID.randomUUID() + "@quoteon.test");
            statement.setString(2, name);
            statement.setString(3, role);
            statement.setString(4, status);
            return statement;
        }, keyHolder);
        return key(keyHolder, "member");
    }

    private long insertCustomer(long memberId) {
        String sql = """
                INSERT INTO customer (member_id, name, customer_type, status)
                VALUES (?, '회귀고객', '기업', 'ACTIVE')
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, memberId);
            return statement;
        }, keyHolder);
        return key(keyHolder, "customer");
    }

    private long insertQuote(long memberId, long customerId, String status, String total, LocalDateTime createdAt) {
        String sql = """
                INSERT INTO quote (
                    quote_number, member_id, customer_id, supply_amount, vat_amount, total_amount,
                    status, quote_date, created_at
                )
                VALUES (?, ?, ?, 0, 0, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, "Q-" + UUID.randomUUID());
            statement.setLong(2, memberId);
            statement.setLong(3, customerId);
            statement.setBigDecimal(4, new BigDecimal(total));
            statement.setString(5, status);
            statement.setObject(6, createdAt.toLocalDate());
            statement.setTimestamp(7, Timestamp.valueOf(createdAt));
            return statement;
        }, keyHolder);
        return key(keyHolder, "quote");
    }

    private void insertItem(long quoteId, String productCode, String productName) {
        insertItem(quoteId, null, productCode, productName);
    }

    private void insertItem(long quoteId, Long productId, String productCode, String productName) {
        jdbcTemplate.update(
                """
                INSERT INTO quote_item (quote_id, product_id, product_name, product_code, sale_price, quantity, amount)
                VALUES (?, ?, ?, ?, 1000, 1, 1000)
                """,
                quoteId,
                productId,
                productName,
                productCode
        );
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

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static long key(KeyHolder keyHolder, String table) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(table + " id was not generated");
        }
        return key.longValue();
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private static int jsonId(long id) {
        return Math.toIntExact(id);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record Account(long id, MockHttpSession session) {
    }
}
