package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import net.likelion.bebc25.quoteon.support.ActiveSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminProductSaleApiTest {

    private static final String USED_PRODUCT_MESSAGE =
            "해당 제품은 견적 내역에서 사용 중이므로 삭제할 수 없습니다. 판매 중지를 이용해 주세요.";
    private static final Path IMAGE_DIRECTORY = Path.of(System.getProperty("user.dir"), "images", "products");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> uploadedPaths = new ArrayList<>();

    @AfterEach
    void deleteUploadedFiles() throws Exception {
        for (String publicPath : uploadedPaths) {
            Files.deleteIfExists(IMAGE_DIRECTORY.resolve(publicPath.substring("/images/products/".length())));
        }
    }

    @Test
    void stopSaleBlocksNewQuotesAndKeepsExistingQuoteItems() throws Exception {
        MockHttpSession admin = loginAdmin();
        SalesMember seller = loginActiveSales();
        long productId = createProduct(admin, "중지제품");
        long customerId = insertCustomer(seller.id(), "중지견적고객");
        long quoteId = createQuote(seller, customerId, productId);
        String itemName = quoteItemValue(quoteId, "product_name");
        String itemCode = quoteItemValue(quoteId, "product_code");
        BigDecimal itemPrice = jdbcTemplate.queryForObject(
                "SELECT sale_price FROM quote_item WHERE quote_id = ?",
                BigDecimal.class,
                quoteId
        );
        int quotes = count("quote");

        mockMvc.perform(post("/api/admin/products/{productId}/stop", productId)
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertEquals("INACTIVE", statusOf(productId));
        assertEquals(itemName, quoteItemValue(quoteId, "product_name"));
        assertEquals(itemCode, quoteItemValue(quoteId, "product_code"));
        assertEquals(0, itemPrice.compareTo(jdbcTemplate.queryForObject(
                "SELECT sale_price FROM quote_item WHERE quote_id = ?",
                BigDecimal.class,
                quoteId
        )));

        mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, itemJson(productId, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ACTIVE"))
                .andExpect(jsonPath("$.message").value("판매 중인 제품만 견적에 포함할 수 있습니다."));
        assertEquals(quotes, count("quote"));

        mockMvc.perform(get("/api/products").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].productCode").value("SH-600"));
        mockMvc.perform(get("/api/admin/products/{productId}", productId).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(post("/api/admin/products/{productId}/stop", productId)
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_STATUS_CONFLICT"))
                .andExpect(jsonPath("$.message").value("판매 중인 제품만 판매 중지할 수 있습니다."));
        assertEquals("INACTIVE", statusOf(productId));

        long missingId = productId + 100_000L;
        mockMvc.perform(post("/api/admin/products/{productId}/stop", missingId)
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void deleteRejectsQuotedProductsAndRemovesUnusedProductsWithImages() throws Exception {
        MockHttpSession admin = loginAdmin();
        SalesMember seller = loginActiveSales();
        long usedProductId = createProduct(admin, "사용제품");
        String usedImage = uploadRepresentative(admin, usedProductId);
        long customerId = insertCustomer(seller.id(), "삭제견적고객");
        createQuote(seller, customerId, usedProductId);
        int products = count("product");
        int items = count("quote_item");

        mockMvc.perform(delete("/api/admin/products/{productId}", usedProductId)
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_IN_USE"))
                .andExpect(jsonPath("$.message").value(USED_PRODUCT_MESSAGE));
        assertEquals(products, count("product"));
        assertEquals(items, count("quote_item"));
        assertEquals("ACTIVE", statusOf(usedProductId));
        assertTrue(Files.exists(imageFile(usedImage)));

        long unusedProductId = createProduct(admin, "미사용제품");
        String representative = uploadRepresentative(admin, unusedProductId);
        String detail = uploadDetail(admin, unusedProductId);
        mockMvc.perform(delete("/api/admin/products/{productId}", unusedProductId)
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(unusedProductId))
                .andExpect(jsonPath("$.imagePath").value(representative))
                .andExpect(jsonPath("$.detailImages", hasSize(1)))
                .andExpect(jsonPath("$.detailImages[0].imagePath").value(detail));
        assertFalse(Files.exists(imageFile(representative)));
        assertFalse(Files.exists(imageFile(detail)));
        mockMvc.perform(get(representative)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/products/{productId}", unusedProductId).session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_image WHERE product_id = ?",
                Integer.class,
                unusedProductId
        ));
        assertEquals(products, count("product"));

        long missingId = unusedProductId + 100_000L;
        mockMvc.perform(delete("/api/admin/products/{productId}", missingId)
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void stopAndDeleteRequireAdminAndCsrf() throws Exception {
        MockHttpSession admin = loginAdmin();
        MockHttpSession sales = ActiveSession.open(mockMvc, jdbcTemplate);
        long productId = createProduct(admin, "권한제품");
        int products = count("product");

        mockMvc.perform(post("/api/admin/products/{productId}/stop", productId).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(delete("/api/admin/products/{productId}", productId).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/products/{productId}/stop", productId)
                        .with(csrf())
                        .session(sales))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/admin/products/{productId}", productId)
                        .with(csrf())
                        .session(sales))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/products/{productId}/stop", productId).session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        mockMvc.perform(delete("/api/admin/products/{productId}", productId).session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertEquals("ACTIVE", statusOf(productId));
        assertEquals(products, count("product"));

        mockMvc.perform(get("/api/products").session(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    private String uploadRepresentative(MockHttpSession admin, long productId) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(jpegFile("photo.jpg"))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andReturn();
        String path = pathOf(result, "$.imagePath");
        uploadedPaths.add(path);
        return path;
    }

    private String uploadDetail(MockHttpSession admin, long productId) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/products/{productId}/detail-images/{slot}", productId, 1)
                        .file(jpegFile("detail.jpg"))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andReturn();
        String path = pathOf(result, "$.imagePath");
        uploadedPaths.add(path);
        return path;
    }

    private long createQuote(SalesMember seller, long customerId, long productId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/quotes")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteJson(customerId, itemJson(productId, 1))))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long createProduct(MockHttpSession admin, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":1,"name":"%s","productCode":"%s","unitPrice":1000,"salePrice":2000}
                                """.formatted(name, "SALE-" + token())))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long insertCustomer(long memberId, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO customer (member_id, name, customer_type, status) VALUES (?, ?, 'COMPANY', 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS
            );
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

    private MockHttpSession loginAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@quoteon.test";
        mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"ADMIN","name":"판매관리자","email":"%s","password":"password1234","phone":"01012345678","companyName":"관리회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE member SET status = 'ACTIVE' WHERE email = ?", email);
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

    private SalesMember loginActiveSales() throws Exception {
        String email = "sales-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"SALES","name":"영업회원","email":"%s","password":"password1234","phone":"01012345678","companyName":"영업회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idOf(signup);
        jdbcTemplate.update("UPDATE member SET status = 'ACTIVE' WHERE id = ?", id);
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new SalesMember(id, (MockHttpSession) login.getRequest().getSession(false));
    }

    private static MockMultipartFile jpegFile(String filename) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return new MockMultipartFile("file", filename, "image/jpeg", output.toByteArray());
    }

    private static Path imageFile(String publicPath) {
        return IMAGE_DIRECTORY.resolve(publicPath.substring("/images/products/".length()));
    }

    private String statusOf(long productId) {
        return jdbcTemplate.queryForObject("SELECT status FROM product WHERE id = ?", String.class, productId);
    }

    private String quoteItemValue(long quoteId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM quote_item WHERE quote_id = ?",
                String.class,
                quoteId
        );
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static String quoteJson(long customerId, String item) {
        return """
                {"customerId":%d,"quoteDate":"2026-10-30","items":[%s]}
                """.formatted(customerId, item);
    }

    private static String itemJson(long productId, int quantity) {
        return """
                {"productId":%d,"quantity":%d}
                """.formatted(productId, quantity);
    }

    private static String pathOf(MvcResult result, String expression) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), expression);
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record SalesMember(long id, MockHttpSession session) {
    }
}
