package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SalesQuoteDocumentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerDownloadsPdfJpegAndPngWithSnapshotAmounts() throws Exception {
        SalesMember seller = loginSales("김영업");
        long shoeId = productId("SH-600");
        long quoteId = createQuote(seller, insertCustomer(seller.id()), itemJson(shoeId, 2));
        String quoteNumber = quoteNumber(quoteId);
        MockHttpSession session = seller.session();

        MvcResult pdf = mockMvc.perform(get("/api/quotes/{id}/pdf", quoteId).session(session))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        MediaType.APPLICATION_PDF_VALUE,
                        result.getResponse().getContentType()
                ))
                .andReturn();
        byte[] pdfBytes = pdf.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 4);
        assertEquals("%PDF", new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII));
        assertEquals("견적서_" + quoteNumber + ".pdf", filename(pdf));

        MvcResult jpeg = mockMvc.perform(get("/api/quotes/{id}/jpeg", quoteId).session(session))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        MediaType.IMAGE_JPEG_VALUE,
                        result.getResponse().getContentType()
                ))
                .andReturn();
        byte[] jpegBytes = jpeg.getResponse().getContentAsByteArray();
        assertEquals("견적서_" + quoteNumber + ".jpg", filename(jpeg));
        assertJpeg(jpegBytes);

        MvcResult png = mockMvc.perform(get("/api/quotes/{id}/png", quoteId).session(session))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(
                        MediaType.IMAGE_PNG_VALUE,
                        result.getResponse().getContentType()
                ))
                .andReturn();
        byte[] pngBytes = png.getResponse().getContentAsByteArray();
        assertEquals("견적서_" + quoteNumber + ".png", filename(png));
        assertPngMatchesFirstPdfPage(pdfBytes, pngBytes);
        assertSameFirstPageSize(pdfBytes, jpegBytes);

        String text = pdfText(pdfBytes);
        assertTrue(text.contains("견적서"));
        assertTrue(text.contains("견적 번호"));
        assertTrue(text.contains(quoteNumber));
        assertTrue(text.contains("견적일"));
        assertTrue(text.contains("2026-10-18"));
        assertTrue(text.contains("담당자"));
        assertTrue(text.contains("김영업"));
        assertTrue(text.contains("고객 정보"));
        assertTrue(text.contains("김진우"));
        assertTrue(text.contains("기업"));
        assertTrue(text.contains("01012345678"));
        assertTrue(text.contains("customer@quoteon.test"));
        assertTrue(text.contains("서울시 강남구"));
        assertTrue(text.contains("화이트 신발장"));
        assertTrue(text.contains("SH-600"));
        assertTrue(text.contains("120,000원"));
        assertTrue(text.contains("240,000원"));
        assertTrue(text.contains("24,000원"));
        assertTrue(text.contains("264,000원"));
        assertTrue(text.contains("판매가"));
        assertTrue(text.contains("공급가액"));
        assertTrue(text.contains("부가세"));
        assertTrue(text.contains("합계"));
        assertFalse(text.contains("작성일"));
        assertFalse(text.contains("총금액"));
        assertFalse(text.contains("공급자"));
        assertFalse(text.contains("결제방법"));
        assertFalse(text.contains("제조사"));
        assertFalse(text.contains("규격"));
        assertFalse(text.contains("유효기간"));
        assertEquals("DRAFT", statusOf(quoteId));
    }

    @Test
    void otherMemberAnonymousAndMissingQuoteAreRejected() throws Exception {
        SalesMember seller = loginSales("김영업");
        SalesMember other = loginSales("이상담");
        long quoteId = createQuote(seller, insertCustomer(seller.id()), itemJson(productId("SH-600"), 1));

        for (String kind : new String[] {"pdf", "jpeg", "png"}) {
            mockMvc.perform(get("/api/quotes/{id}/" + kind, quoteId).session(other.session()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("QUOTE_ACCESS_DENIED"));
            mockMvc.perform(get("/api/quotes/{id}/" + kind, quoteId))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mockMvc.perform(get("/api/quotes/{id}/" + kind, 999999999L).session(seller.session()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));
        }
    }

    @Test
    void downloadKeepsDraftAndSentAndUsesItemSnapshot() throws Exception {
        SalesMember seller = loginSales("김영업");
        long shoeId = productId("SH-600");
        long draftId = createQuote(seller, insertCustomer(seller.id()), itemJson(shoeId, 1));
        long sentId = createQuote(seller, insertCustomer(seller.id()), itemJson(shoeId, 1));
        jdbcTemplate.update("UPDATE quote SET status = 'SENT' WHERE id = ?", sentId);
        LocalDateTime draftUpdatedAt = updatedAt(draftId);
        LocalDateTime sentUpdatedAt = updatedAt(sentId);
        MockHttpSession session = seller.session();

        String before = pdfText(download(session, draftId, "pdf"));
        download(session, draftId, "jpeg");
        download(session, draftId, "png");
        assertEquals("DRAFT", statusOf(draftId));
        assertEquals(draftUpdatedAt, updatedAt(draftId));

        download(session, sentId, "pdf");
        download(session, sentId, "jpeg");
        download(session, sentId, "png");
        assertEquals("SENT", statusOf(sentId));
        assertEquals(sentUpdatedAt, updatedAt(sentId));

        jdbcTemplate.update(
                "UPDATE product SET name = ?, product_code = ?, sale_price = ? WHERE id = ?",
                "변경된 제품",
                "NEW-" + token(),
                new BigDecimal("1.00"),
                shoeId
        );
        String afterChange = pdfText(download(session, draftId, "pdf"));
        assertTrue(afterChange.contains("화이트 신발장"));
        assertTrue(afterChange.contains("SH-600"));
        assertTrue(afterChange.contains("120,000원"));
        assertFalse(afterChange.contains("변경된 제품"));
        assertTrue(before.contains("화이트 신발장"));
        assertEquals("DRAFT", statusOf(draftId));
        assertEquals("SENT", statusOf(sentId));
    }

    @Test
    void longQuoteFlowsOntoTheNextPageAndImagesKeepTheFirstPage() throws Exception {
        SalesMember seller = loginSales("김영업");
        long quoteId = createQuote(
                seller,
                insertCustomer(seller.id()),
                itemJson(productId("SH-600"), 1)
        );
        for (int index = 0; index < 40; index++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO quote_item (
                        quote_id, product_id, product_name, product_code, sale_price, quantity, amount
                    )
                    VALUES (?, NULL, ?, ?, ?, 1, ?)
                    """,
                    quoteId,
                    "추가제품" + index,
                    "ADD-" + index,
                    new BigDecimal("1000.00"),
                    new BigDecimal("1000.00")
            );
        }
        MockHttpSession session = seller.session();
        byte[] pdf = download(session, quoteId, "pdf");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() >= 2);
        }
        String firstPage = pdfText(pdf, 1, 1);
        String secondPage = pdfText(pdf, 2, 2);
        assertTrue(firstPage.contains("화이트 신발장"));
        assertTrue(secondPage.contains("추가제품39"));
        assertFalse(firstPage.contains("추가제품39"));
        assertSameFirstPageSize(pdf, download(session, quoteId, "png"));
        assertSameFirstPageSize(pdf, download(session, quoteId, "jpeg"));
        assertEquals("DRAFT", statusOf(quoteId));
    }

    private void assertJpeg(byte[] jpeg) {
        assertTrue(jpeg.length > 3);
        assertEquals((byte) 0xFF, jpeg[0]);
        assertEquals((byte) 0xD8, jpeg[1]);
        assertEquals((byte) 0xFF, jpeg[2]);
    }

    private void assertPngMatchesFirstPdfPage(byte[] pdf, byte[] png) throws Exception {
        BufferedImage actual = ImageIO.read(new ByteArrayInputStream(png));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage expected = new PDFRenderer(document).renderImageWithDPI(0, 200, ImageType.RGB);
            assertEquals(expected.getWidth(), actual.getWidth());
            assertEquals(expected.getHeight(), actual.getHeight());
            for (int y = 0; y < expected.getHeight(); y += 20) {
                for (int x = 0; x < expected.getWidth(); x += 20) {
                    assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "x=" + x + ", y=" + y);
                }
            }
        }
    }

    private void assertSameFirstPageSize(byte[] pdf, byte[] image) throws Exception {
        BufferedImage actual = ImageIO.read(new ByteArrayInputStream(image));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage expected = new PDFRenderer(document).renderImageWithDPI(0, 200, ImageType.RGB);
            assertEquals(expected.getWidth(), actual.getWidth());
            assertEquals(expected.getHeight(), actual.getHeight());
        }
    }

    private byte[] download(MockHttpSession session, long quoteId, String kind) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/quotes/{id}/" + kind, quoteId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsByteArray();
    }

    private static String filename(MvcResult result) {
        String header = result.getResponse().getHeader("Content-Disposition");
        ContentDisposition disposition = ContentDisposition.parse(header);
        assertEquals("attachment", disposition.getType());
        return disposition.getFilename();
    }

    private static String pdfText(byte[] pdf) throws Exception {
        return pdfText(pdf, 1, Integer.MAX_VALUE);
    }

    private static String pdfText(byte[] pdf, int startPage, int endPage) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(Math.min(endPage, document.getNumberOfPages()));
            return stripper.getText(document);
        }
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
                                {"customerId":%d,"quoteDate":"2026-10-18","items":[%s]}
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

    private long insertCustomer(long memberId) {
        String sql = """
                INSERT INTO customer (member_id, name, customer_type, phone, email, address, memo, status)
                VALUES (?, '김진우', '기업', '01012345678', 'customer@quoteon.test', '서울시 강남구', '고객 메모', 'ACTIVE')
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
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

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private static String itemJson(long productId, int quantity) {
        return """
                {"productId":%d,"quantity":%d}
                """.formatted(productId, quantity);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record SalesMember(long id, MockHttpSession session) {
    }
}
