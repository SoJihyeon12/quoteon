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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminProductApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminCanListProductsWithSearchCategoryPriceStatusAndPage() throws Exception {
        MockHttpSession admin = loginAdmin().session();
        long stoppedId = insertProduct(1L, "STOP-" + token(), "중지선반", "90000.00", "INACTIVE");

        mockMvc.perform(get("/api/admin/products").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].imagePath").value("/images/products/sh-600.jpg"))
                .andExpect(jsonPath("$.items[0].name").value("화이트 신발장"))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[0].salePrice").value(120000.00))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].categoryId").doesNotExist())
                .andExpect(jsonPath("$.items[3].id").value(stoppedId))
                .andExpect(jsonPath("$.items[3].status").value("INACTIVE"))
                .andExpect(jsonPath("$.items[3].imagePath").value(nullValue()));

        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("keyword", "신발장"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("keyword", "OF-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].name").value("사무용 책상"));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("keyword", "1828"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("keyword", "원목 소재"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-800"));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("keyword", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[1].productCode").value("SH-800"))
                .andExpect(jsonPath("$.items[2].id").value(stoppedId));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("categoryId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("salePrice", "120000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(stoppedId));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[1].productCode").value("SH-800"));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productCode").value("OF-100"));

        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));

        MockHttpSession sales = ActiveSession.open(mockMvc, jdbcTemplate);
        mockMvc.perform(get("/api/products").session(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("전자제품"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[2].productCode").value("OF-100"));
    }

    @Test
    void categoryFilterUsesTheSelectedCategoryOnly() throws Exception {
        Admin admin = loginAdmin();
        MvcResult child = mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"신발장하위\",\"parentId\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        long childId = idOf(child);
        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(childId, "하위제품", "CHILD-" + token(), "1000.00", "2000.00", null, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/products")
                        .session(admin.session())
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[1].productCode").value("SH-800"));
        mockMvc.perform(get("/api/admin/products")
                        .session(admin.session())
                        .param("categoryId", Long.toString(childId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].name").value("하위제품"));
    }

    @Test
    void adminCanReadProductDetailAndMissingProductIsNotFound() throws Exception {
        MockHttpSession admin = loginAdmin().session();

        mockMvc.perform(get("/api/admin/products/1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.categoryName").value("전자제품"))
                .andExpect(jsonPath("$.name").value("화이트 신발장"))
                .andExpect(jsonPath("$.productCode").value("SH-600"))
                .andExpect(jsonPath("$.unitPrice").value(80000.00))
                .andExpect(jsonPath("$.salePrice").value(120000.00))
                .andExpect(jsonPath("$.specification").value("600x330x1828mm"))
                .andExpect(jsonPath("$.description").value("화이트 색상의 신발장"))
                .andExpect(jsonPath("$.imagePath").value("/images/products/sh-600.jpg"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        long missingId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM product", Long.class) + 100_000L;
        mockMvc.perform(get("/api/admin/products/" + missingId).session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("제품을 찾을 수 없습니다."));
    }

    @Test
    void adminCanCreateProductAndValidationRejectsRequiredFields() throws Exception {
        MockHttpSession admin = loginAdmin().session();
        String code = "NEW-" + token();

        MvcResult created = mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(2L, "  신규 책상  ", " " + code + " ", "90000.00", "135000.50", "  ", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(2))
                .andExpect(jsonPath("$.categoryName").value("사무용품"))
                .andExpect(jsonPath("$.name").value("신규 책상"))
                .andExpect(jsonPath("$.productCode").value(code))
                .andExpect(jsonPath("$.unitPrice").value(90000.00))
                .andExpect(jsonPath("$.salePrice").value(135000.50))
                .andExpect(jsonPath("$.specification").value(nullValue()))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.imagePath").value(nullValue()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        long productId = idOf(created);
        assertEquals("ACTIVE", statusOf(productId));
        assertEquals(null, imagePathOf(productId));

        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(1L, " ", "CODE-" + token(), "1000", "2000", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("제품명을 입력해 주세요."));
        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":1,"name":"코드없음","unitPrice":1000,"salePrice":2000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("제품 코드를 입력해 주세요."));
        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":1,"name":"단가없음","productCode":"NO-UNIT","salePrice":2000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("단가를 입력해 주세요."));
        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":1,"name":"판매가없음","productCode":"NO-SALE","unitPrice":1000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("판매 가격을 입력해 주세요."));
        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"카테고리없음","productCode":"NO-CAT","unitPrice":1000,"salePrice":2000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("카테고리를 선택해 주세요."));

        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(1L, "중복코드", "SH-600", "1000", "2000", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CODE_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 제품 코드입니다."));
        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(999_999L, "없는분류", "MISS-" + token(), "1000", "2000", null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("카테고리를 찾을 수 없습니다."));
    }

    @Test
    void updateChangesProductFieldsAndKeepsStatusImageAndQuoteItem() throws Exception {
        Admin admin = loginAdmin();
        String code = "EDIT-" + token();
        MvcResult created = mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(1L, "수정전", code, "1000.00", "2000.00", "규격", "설명")))
                .andExpect(status().isCreated())
                .andReturn();
        long productId = idOf(created);
        jdbcTemplate.update(
                "UPDATE product SET image_path = ?, status = 'INACTIVE' WHERE id = ?",
                "/images/products/kept.jpg",
                productId
        );
        long quoteItemId = insertQuoteItem(admin.id(), productId, "수정전", code, "2000.00");
        int quoteItems = count("quote_item");
        int quotes = count("quote");

        mockMvc.perform(put("/api/admin/products/" + productId)
                        .with(csrf())
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":2,"name":"수정후","productCode":"%s","unitPrice":3000.00,"salePrice":4000.00,"specification":"변경규격","description":"변경설명","status":"ACTIVE","imagePath":"/images/products/ignored.jpg"}
                                """.formatted("NEW-" + token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(2))
                .andExpect(jsonPath("$.categoryName").value("사무용품"))
                .andExpect(jsonPath("$.name").value("수정후"))
                .andExpect(jsonPath("$.unitPrice").value(3000.00))
                .andExpect(jsonPath("$.salePrice").value(4000.00))
                .andExpect(jsonPath("$.specification").value("변경규격"))
                .andExpect(jsonPath("$.description").value("변경설명"))
                .andExpect(jsonPath("$.imagePath").value("/images/products/kept.jpg"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        assertEquals("INACTIVE", statusOf(productId));
        assertEquals("/images/products/kept.jpg", imagePathOf(productId));
        assertEquals(quoteItems, count("quote_item"));
        assertEquals(quotes, count("quote"));
        assertEquals("수정전", jdbcTemplate.queryForObject(
                "SELECT product_name FROM quote_item WHERE id = ?",
                String.class,
                quoteItemId
        ));
        assertEquals(code, jdbcTemplate.queryForObject(
                "SELECT product_code FROM quote_item WHERE id = ?",
                String.class,
                quoteItemId
        ));
        assertEquals(0, new BigDecimal("2000.00").compareTo(jdbcTemplate.queryForObject(
                "SELECT sale_price FROM quote_item WHERE id = ?",
                BigDecimal.class,
                quoteItemId
        )));

        mockMvc.perform(put("/api/admin/products/" + productId)
                        .with(csrf())
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(2L, "중복코드", "SH-600", "1", "2", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CODE_EXISTS"));

        String currentCode = jdbcTemplate.queryForObject(
                "SELECT product_code FROM product WHERE id = ?",
                String.class,
                productId
        );
        mockMvc.perform(put("/api/admin/products/" + productId)
                        .with(csrf())
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(2L, "본인코드", currentCode, "11.00", "22.00", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value(currentCode))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        long missingId = productId + 100_000L;
        mockMvc.perform(put("/api/admin/products/" + missingId)
                        .with(csrf())
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(1L, "없음", "MISS-" + token(), "1", "2", null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(put("/api/admin/products/" + productId)
                        .with(csrf())
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(999_999L, "없는분류", "CAT-" + token(), "1", "2", null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void adminProductApisRequireAdminAndCsrf() throws Exception {
        Admin admin = loginAdmin();
        MockHttpSession sales = ActiveSession.open(mockMvc, jdbcTemplate);
        int products = count("product");
        String body = productJson(1L, "권한제품", "AUTH-" + token(), "1000", "2000", null, null);

        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/admin/products/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(put("/api/admin/products/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/products").session(sales))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/products/1").session(sales))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/admin/products/1")
                        .with(csrf())
                        .session(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/products")
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertEquals(products, count("product"));
        String originalName = jdbcTemplate.queryForObject("SELECT name FROM product WHERE id = 1", String.class);
        mockMvc.perform(put("/api/admin/products/1")
                        .session(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(1L, "변경되면안됨", "SH-600", "1", "2", null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertEquals(originalName, jdbcTemplate.queryForObject("SELECT name FROM product WHERE id = 1", String.class));
    }

    private long insertProduct(long categoryId, String code, String name, String salePrice, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO product (category_id, product_code, name, unit_price, sale_price, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                categoryId,
                code,
                name,
                new BigDecimal("1000.00"),
                new BigDecimal(salePrice),
                status
        );
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE product_code = ?", Long.class, code);
    }

    private long insertQuoteItem(long memberId, long productId, String name, String code, String salePrice) {
        jdbcTemplate.update(
                """
                INSERT INTO customer (member_id, name, customer_type)
                VALUES (?, '견적고객', '기업')
                """,
                memberId
        );
        long customerId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM customer", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO quote (quote_number, member_id, customer_id, quote_date)
                VALUES (?, ?, ?, CURRENT_DATE)
                """,
                "Q-" + token(),
                memberId,
                customerId
        );
        long quoteId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM quote", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO quote_item (quote_id, product_id, product_name, product_code, sale_price, quantity, amount)
                VALUES (?, ?, ?, ?, ?, 2, ?)
                """,
                quoteId,
                productId,
                name,
                code,
                new BigDecimal(salePrice),
                new BigDecimal(salePrice).multiply(BigDecimal.valueOf(2))
        );
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM quote_item", Long.class);
    }

    private Admin loginAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"ADMIN","name":"제품관리자","email":"%s","password":"password1234","phone":"01012345678","companyName":"관리회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'",
                id.longValue()
        );
        if (updated != 1) {
            throw new IllegalStateException("test admin was not activated");
        }
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new Admin(id.longValue(), (MockHttpSession) login.getRequest().getSession(false));
    }

    private static String productJson(
            long categoryId,
            String name,
            String productCode,
            String unitPrice,
            String salePrice,
            String specification,
            String description
    ) {
        return """
                {"categoryId":%d,"name":"%s","productCode":"%s","unitPrice":%s,"salePrice":%s,"specification":%s,"description":%s}
                """.formatted(
                categoryId,
                name,
                productCode,
                unitPrice,
                salePrice,
                jsonString(specification),
                jsonString(description)
        );
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value + "\"";
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private String statusOf(long productId) {
        return jdbcTemplate.queryForObject("SELECT status FROM product WHERE id = ?", String.class, productId);
    }

    private String imagePathOf(long productId) {
        return jdbcTemplate.queryForObject("SELECT image_path FROM product WHERE id = ?", String.class, productId);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Admin(long id, MockHttpSession session) {
    }
}
