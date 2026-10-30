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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminProductImageApiTest {

    private static final Path IMAGE_DIRECTORY = Path.of(System.getProperty("user.dir"), "images", "products");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> uploadedPaths = new ArrayList<>();

    @AfterEach
    void deleteUploadedFiles() throws Exception {
        for (String publicPath : uploadedPaths) {
            Files.deleteIfExists(IMAGE_DIRECTORY.resolve(fileName(publicPath)));
        }
        uploadedPaths.clear();
    }

    @Test
    void uploadsAndReplacesRepresentativeImage() throws Exception {
        MockHttpSession admin = loginAdmin();
        long productId = createProduct(admin);
        byte[] jpeg = jpegBytes();
        byte[] png = pngBytes();

        MvcResult created = mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("photo.jpg", "image/jpeg", jpeg))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.imagePath").exists())
                .andExpect(jsonPath("$.detailImages", hasSize(0)))
                .andReturn();
        String firstPath = pathOf(created, "$.imagePath");
        remember(firstPath);
        assertTrue(firstPath.startsWith("/images/products/"));
        assertFalse(firstPath.contains("photo.jpg"));
        assertFalse(firstPath.contains(":"));
        assertEquals(firstPath, imagePathOf(productId));
        assertTrue(Files.exists(IMAGE_DIRECTORY.resolve(fileName(firstPath))));
        assertArrayEquals(jpeg, Files.readAllBytes(IMAGE_DIRECTORY.resolve(fileName(firstPath))));

        MvcResult replaced = mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("photo.png", "image/png", png))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePath").exists())
                .andReturn();
        String secondPath = pathOf(replaced, "$.imagePath");
        remember(secondPath);
        assertNotEquals(firstPath, secondPath);
        assertEquals(secondPath, imagePathOf(productId));
        assertFalse(Files.exists(IMAGE_DIRECTORY.resolve(fileName(firstPath))));
        assertTrue(Files.exists(IMAGE_DIRECTORY.resolve(fileName(secondPath))));

        mockMvc.perform(get(secondPath))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(png));
    }

    @Test
    void rejectsInvalidRepresentativeFilesAndMissingProduct() throws Exception {
        MockHttpSession admin = loginAdmin();
        long productId = createProduct(admin);
        long missingId = productId + 100_000L;

        mockMvc.perform(multipart("/api/admin/products/{productId}/image", missingId)
                        .file(imageFile("a.jpg", "image/jpeg", jpegBytes()))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("empty.jpg", "image/jpeg", new byte[0]))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"))
                .andExpect(jsonPath("$.message").value("빈 파일은 업로드할 수 없습니다."));
        mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("big.jpg", "image/jpeg", new byte[(int) (5L * 1024 * 1024 + 1)]))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"))
                .andExpect(jsonPath("$.message").value("파일 크기는 5MB 이하여야 합니다."));
        mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("anim.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII)))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"))
                .andExpect(jsonPath("$.message").value("JPEG 또는 PNG 이미지만 업로드할 수 있습니다."));
        mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("fake.jpg", "image/jpeg", "not-an-image".getBytes(StandardCharsets.UTF_8)))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("real.png", "image/png", pngBytes()))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk());
        String stored = imagePathOf(productId);
        remember(stored);
        assertTrue(stored.endsWith(".png"));
        assertEquals(0, countDetailImages(productId));
    }

    @Test
    void uploadsAndReplacesDetailImageSlotsIndependently() throws Exception {
        MockHttpSession admin = loginAdmin();
        long productId = createProduct(admin);

        String first = uploadDetail(admin, productId, 1, "one.jpg", jpegBytes());
        String second = uploadDetail(admin, productId, 2, "two.png", pngBytes());
        String third = uploadDetail(admin, productId, 3, "three.jpg", jpegBytes());
        assertEquals(3, countDetailImages(productId));

        String replaced = uploadDetail(admin, productId, 1, "one-new.png", pngBytes());
        assertNotEquals(first, replaced);
        assertFalse(Files.exists(IMAGE_DIRECTORY.resolve(fileName(first))));
        assertEquals(second, detailPath(productId, 2));
        assertEquals(third, detailPath(productId, 3));
        assertEquals(replaced, detailPath(productId, 1));
        assertEquals(3, countDetailImages(productId));

        mockMvc.perform(get("/api/admin/products/{productId}", productId).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePath").value(nullValue()))
                .andExpect(jsonPath("$.detailImages", hasSize(3)))
                .andExpect(jsonPath("$.detailImages[0].sortOrder").value(1))
                .andExpect(jsonPath("$.detailImages[0].imagePath").value(replaced))
                .andExpect(jsonPath("$.detailImages[1].sortOrder").value(2))
                .andExpect(jsonPath("$.detailImages[1].imagePath").value(second))
                .andExpect(jsonPath("$.detailImages[2].sortOrder").value(3))
                .andExpect(jsonPath("$.detailImages[2].imagePath").value(third));

        mockMvc.perform(multipart("/api/admin/products/{productId}/detail-images/{slot}", productId, 4)
                        .file(imageFile("four.jpg", "image/jpeg", jpegBytes()))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("상세 이미지 슬롯은 1, 2, 3만 선택할 수 있습니다."));
        assertEquals(3, countDetailImages(productId));
    }

    @Test
    void imageLookupRejectsMissingAndEscapingPaths() throws Exception {
        mockMvc.perform(get("/images/products/{filename}", "00000000-0000-0000-0000-000000000000.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
        mockMvc.perform(get("/images/products/{filename}", "foo..bar.jpg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE_PATH"));

        MvcResult traversal = mockMvc.perform(get("/images/products/{filename}", "../schema.sql"))
                .andReturn();
        assertNotEquals(200, traversal.getResponse().getStatus());
        assertFalse(traversal.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("CREATE TABLE"));
    }

    @Test
    void uploadRequiresAdminAndCsrfAndPreservesExistingBehavior() throws Exception {
        MockHttpSession admin = loginAdmin();
        MockHttpSession sales = ActiveSession.open(mockMvc, jdbcTemplate);
        byte[] jpeg = jpegBytes();
        int productsBefore = count("product");
        int quotesBefore = count("quote");
        int itemsBefore = count("quote_item");

        mockMvc.perform(get("/api/products").session(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$[0].detailImages").doesNotExist());
        mockMvc.perform(get("/api/admin/products").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[0].imagePath").value("/images/products/sh-600.jpg"))
                .andExpect(jsonPath("$.items[0].detailImages").doesNotExist());

        long productId = createProduct(admin);
        long quoteItemId = insertQuoteItem(adminMemberId(), productId);
        String itemName = jdbcTemplate.queryForObject(
                "SELECT product_name FROM quote_item WHERE id = ?", String.class, quoteItemId);
        String itemCode = jdbcTemplate.queryForObject(
                "SELECT product_code FROM quote_item WHERE id = ?", String.class, quoteItemId);
        BigDecimal itemPrice = jdbcTemplate.queryForObject(
                "SELECT sale_price FROM quote_item WHERE id = ?", BigDecimal.class, quoteItemId);

        mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("a.jpg", "image/jpeg", jpeg))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(multipart("/api/admin/products/{productId}/detail-images/{slot}", productId, 1)
                        .file(imageFile("a.jpg", "image/jpeg", jpeg))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("a.jpg", "image/jpeg", jpeg))
                        .with(csrf())
                        .session(sales))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(multipart("/api/admin/products/{productId}/detail-images/{slot}", productId, 1)
                        .file(imageFile("a.jpg", "image/jpeg", jpeg))
                        .with(csrf())
                        .session(sales))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("a.jpg", "image/jpeg", jpeg))
                        .session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertEquals(null, imagePathOf(productId));

        String representative = pathOf(mockMvc.perform(multipart("/api/admin/products/{productId}/image", productId)
                        .file(imageFile("a.jpg", "image/jpeg", jpeg))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andReturn(), "$.imagePath");
        remember(representative);
        String detail = uploadDetail(admin, productId, 2, "b.png", pngBytes());

        mockMvc.perform(put("/api/admin/products/{productId}", productId)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(1L, "수정이름", "KEEP-" + token(), "10.00", "20.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePath").value(representative))
                .andExpect(jsonPath("$.detailImages", hasSize(1)))
                .andExpect(jsonPath("$.detailImages[0].imagePath").value(detail))
                .andExpect(jsonPath("$.detailImages[0].sortOrder").value(2));

        assertEquals(itemName, jdbcTemplate.queryForObject(
                "SELECT product_name FROM quote_item WHERE id = ?", String.class, quoteItemId));
        assertEquals(itemCode, jdbcTemplate.queryForObject(
                "SELECT product_code FROM quote_item WHERE id = ?", String.class, quoteItemId));
        assertEquals(0, itemPrice.compareTo(jdbcTemplate.queryForObject(
                "SELECT sale_price FROM quote_item WHERE id = ?", BigDecimal.class, quoteItemId)));
        assertEquals(quotesBefore, count("quote") - 1);
        assertEquals(itemsBefore, count("quote_item") - 1);
        assertEquals(productsBefore, count("product") - 1);
        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'product_image'
                """,
                Integer.class
        ));
    }

    private String uploadDetail(MockHttpSession admin, long productId, int slot, String filename, byte[] bytes)
            throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/products/{productId}/detail-images/{slot}", productId, slot)
                        .file(imageFile(filename, filename.endsWith(".png") ? "image/png" : "image/jpeg", bytes))
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.sortOrder").value(slot))
                .andReturn();
        String path = pathOf(result, "$.imagePath");
        remember(path);
        assertTrue(path.startsWith("/images/products/"));
        assertFalse(path.contains(filename));
        return path;
    }

    private long createProduct(MockHttpSession admin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(1L, "이미지제품", "IMG-" + token(), "1000", "2000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imagePath").value(nullValue()))
                .andExpect(jsonPath("$.detailImages", hasSize(0)))
                .andReturn();
        return idOf(result);
    }

    private long insertQuoteItem(long memberId, long productId) {
        jdbcTemplate.update(
                "INSERT INTO customer (member_id, name, customer_type) VALUES (?, '이미지고객', '기업')",
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
                INSERT INTO quote_item (
                    quote_id, product_id, product_name, product_code, sale_price, quantity, amount
                )
                VALUES (?, ?, '원래이름', 'ORIG', 2000.00, 1, 2000.00)
                """,
                quoteId,
                productId
        );
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM quote_item", Long.class);
    }

    private MockHttpSession loginAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"ADMIN","name":"이미지관리자","email":"%s","password":"password1234","phone":"01012345678","companyName":"관리회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        adminMemberId = idOf(signup);
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'",
                adminMemberId
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
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private long adminMemberId;

    private long adminMemberId() {
        return adminMemberId;
    }

    private static MockMultipartFile imageFile(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", filename, contentType, bytes);
    }

    private static byte[] jpegBytes() throws Exception {
        return imageBytes("jpg");
    }

    private static byte[] pngBytes() throws Exception {
        return imageBytes("png");
    }

    private static byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IllegalStateException("test image was not written");
        }
        return output.toByteArray();
    }

    private void remember(String publicPath) {
        uploadedPaths.add(publicPath);
    }

    private static String pathOf(MvcResult result, String expression) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), expression);
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private static String fileName(String publicPath) {
        return publicPath.substring("/images/products/".length());
    }

    private String imagePathOf(long productId) {
        return jdbcTemplate.queryForObject("SELECT image_path FROM product WHERE id = ?", String.class, productId);
    }

    private String detailPath(long productId, int slot) {
        return jdbcTemplate.queryForObject(
                "SELECT image_path FROM product_image WHERE product_id = ? AND sort_order = ?",
                String.class,
                productId,
                slot
        );
    }

    private int countDetailImages(long productId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_image WHERE product_id = ?",
                Integer.class,
                productId
        );
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private static String productJson(long categoryId, String name, String productCode, String unitPrice, String salePrice) {
        return """
                {"categoryId":%d,"name":"%s","productCode":"%s","unitPrice":%s,"salePrice":%s}
                """.formatted(categoryId, name, productCode, unitPrice, salePrice);
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
