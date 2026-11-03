package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteCustomerResponse;
import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteDetailResponse;
import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteItemResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class QuoteDocumentRenderer {

    static final int PNG_DPI = 200;

    private static final float MARGIN = 48f;
    private static final float TITLE_SIZE = 18f;
    private static final float BODY_SIZE = 10f;
    private static final float LINE = 16f;
    private static final float LABEL_WIDTH = 72f;
    private static final float CELL_PADDING = 4f;
    /** 첫 줄 baseline이 위쪽 구분선과 겹치지 않도록 확보하는 상단 여백. */
    private static final float FORMAL_CELL_TOP = 12f;
    /** 마지막 줄과 아래 구분선 사이 여백. */
    private static final float FORMAL_CELL_BOTTOM = 8f;
    private static final String FONT_PATH = "/fonts/NanumGothic-Regular.ttf";

    private enum Align {
        LEFT,
        CENTER,
        RIGHT
    }

    private static byte[] fontBytes;

    byte[] pdf(AdminQuoteDetailResponse quote) {
        return render(quote, "작성일", "단가", "총금액", true);
    }

    byte[] salesPdf(AdminQuoteDetailResponse quote) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = PDType0Font.load(document, new ByteArrayInputStream(fontBytes()), true);
            try (Canvas canvas = new Canvas(document, font)) {
                canvas.drawFormal(quote);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (QuoteDocumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new QuoteDocumentException(exception);
        }
    }

    private byte[] render(
            AdminQuoteDetailResponse quote,
            String dateLabel,
            String priceLabel,
            String totalLabel,
            boolean includeSupplier
    ) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = PDType0Font.load(document, new ByteArrayInputStream(fontBytes()), true);
            try (Canvas canvas = new Canvas(document, font)) {
                canvas.draw(quote, dateLabel, priceLabel, totalLabel, includeSupplier);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (QuoteDocumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new QuoteDocumentException(exception);
        }
    }

    byte[] png(byte[] pdf) {
        return image(pdf, "png", "PNG 인코더를 찾지 못했습니다.");
    }

    byte[] jpeg(byte[] pdf) {
        return image(pdf, "jpeg", "JPEG 인코더를 찾지 못했습니다.");
    }

    private byte[] image(byte[] pdf, String format, String missingEncoder) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, PNG_DPI, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new QuoteDocumentException(missingEncoder);
            }
            return out.toByteArray();
        } catch (QuoteDocumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new QuoteDocumentException(exception);
        }
    }

    private static byte[] fontBytes() {
        if (fontBytes != null) {
            return fontBytes;
        }
        try (InputStream input = QuoteDocumentRenderer.class.getResourceAsStream(FONT_PATH)) {
            if (input == null) {
                throw new QuoteDocumentException("한국어 폰트를 찾지 못했습니다.");
            }
            fontBytes = input.readAllBytes();
            return fontBytes;
        } catch (IOException exception) {
            throw new QuoteDocumentException(exception);
        }
    }

    private static String won(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.KOREA));
        return format.format(amount) + "원";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private final class Canvas implements AutoCloseable {

        private final PDDocument document;
        private final PDFont font;
        private final float pageWidth;
        private final float pageHeight;
        private final float[] columns;
        private final float[] formalColumns;
        private PDPageContentStream stream;
        private float y;
        private boolean repeatTableHeader;
        private boolean formalMode;
        private String priceLabel = "단가";

        private Canvas(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            this.pageWidth = PDRectangle.A4.getWidth();
            this.pageHeight = PDRectangle.A4.getHeight();
            float tableWidth = pageWidth - (MARGIN * 2);
            this.columns = new float[] {
                    tableWidth * 0.34f,
                    tableWidth * 0.20f,
                    tableWidth * 0.18f,
                    tableWidth * 0.10f,
                    tableWidth * 0.18f
            };
            // NO | 제품명 | 제품코드 | 제조사 | 제품가격 | 수량 | 금액 — 제품명·제품코드에 공간 우선
            this.formalColumns = new float[] {
                    tableWidth * 0.05f,
                    tableWidth * 0.28f,
                    tableWidth * 0.18f,
                    tableWidth * 0.16f,
                    tableWidth * 0.13f,
                    tableWidth * 0.07f,
                    tableWidth * 0.13f
            };
            newPage();
        }

        private void drawFormal(AdminQuoteDetailResponse quote) throws IOException {
            formalMode = true;
            drawCentered("견적서", TITLE_SIZE);
            y -= LINE * 0.8f;
            paintMetaBox(quote);
            y -= LINE * 0.8f;
            paintSupplierTable();
            y -= LINE * 0.6f;
            ensure(LINE);
            drawText(MARGIN, y, WireframeSupplierExample.INTRO, BODY_SIZE);
            y -= LINE * 1.1f;
            ensure(LINE + 8);
            paintFormalTableHeader();
            repeatTableHeader = true;
            int index = 1;
            for (AdminQuoteItemResponse item : quote.items()) {
                paintFormalItem(index++, item);
            }
            repeatTableHeader = false;
            y -= LINE * 0.6f;
            ensure(LINE * 4);
            total("공급가액 합계", quote.supplyAmount());
            total("VAT(10%)", quote.vatAmount());
            total("총 주문금액", quote.totalAmount());
            y -= LINE * 0.4f;
            ensure(LINE);
            drawText(MARGIN, y, WireframeSupplierExample.VALIDITY_NOTICE, BODY_SIZE);
            y -= LINE;
            formalMode = false;
        }

        private void draw(
                AdminQuoteDetailResponse quote,
                String dateLabel,
                String priceLabel,
                String totalLabel,
                boolean includeSupplier
        ) throws IOException {
            formalMode = false;
            drawCentered("견적서", TITLE_SIZE);
            y -= LINE;
            field("견적 번호", quote.quoteNumber());
            field(dateLabel, quote.quoteDate() == null ? "" : quote.quoteDate().toString());
            field("담당자", quote.authorName());
            y -= LINE * 0.5f;
            heading("고객 정보");
            AdminQuoteCustomerResponse customer = quote.customer();
            field("고객명", customer.name());
            field("고객 유형", customer.customerType());
            field("전화번호", customer.phone());
            field("이메일", customer.email());
            field("주소", customer.address());
            if (includeSupplier) {
                y -= LINE * 0.5f;
                heading("공급자");
                for (String[] supplierField : WireframeSupplierExample.FIELDS) {
                    field(supplierField[0], supplierField[1]);
                }
            }
            y -= LINE * 0.5f;
            ensure(LINE + 8);
            paintTableHeader(priceLabel);
            repeatTableHeader = true;
            for (AdminQuoteItemResponse item : quote.items()) {
                paintItem(item);
            }
            repeatTableHeader = false;
            y -= LINE * 0.5f;
            ensure(LINE * 3);
            total("공급가액", quote.supplyAmount());
            total("부가세", quote.vatAmount());
            total(totalLabel, quote.totalAmount());
        }

        private void paintMetaBox(AdminQuoteDetailResponse quote) throws IOException {
            float boxWidth = 210f;
            float boxX = pageWidth - MARGIN - boxWidth;
            String manager = text(quote.authorName());
            String customer = text(quote.customer() == null ? null : quote.customer().name());
            String date = quote.quoteDate() == null ? "" : quote.quoteDate().toString();
            String[][] rows = {
                    {"담당자", manager},
                    {"고객", customer},
                    {"견적일", date},
                    {"견적금액", won(quote.totalAmount())}
            };
            float rowHeight = LINE + 4f;
            float boxHeight = rowHeight * rows.length;
            ensure(boxHeight + 4);
            float top = y + 4;
            float bottom = top - boxHeight;
            stream.setStrokingColor(0.2f);
            stream.addRect(boxX, bottom, boxWidth, boxHeight);
            stream.stroke();
            float labelWidth = 64f;
            float rowY = top;
            for (String[] row : rows) {
                rowY -= rowHeight;
                stream.moveTo(boxX, rowY);
                stream.lineTo(boxX + boxWidth, rowY);
                stream.stroke();
                stream.moveTo(boxX + labelWidth, rowY);
                stream.lineTo(boxX + labelWidth, rowY + rowHeight);
                stream.stroke();
                drawText(boxX + CELL_PADDING, rowY + 6, row[0], BODY_SIZE);
                drawText(boxX + labelWidth + CELL_PADDING, rowY + 6, row[1], BODY_SIZE);
            }
            y = bottom - LINE * 0.3f;
        }

        private void paintSupplierTable() throws IOException {
            float tableWidth = pageWidth - (MARGIN * 2);
            float groupWidth = 48f;
            float labelWidth = 64f;
            float valueWidth = tableWidth - groupWidth - labelWidth;
            float rowHeight = LINE + 2f;
            float tableHeight = rowHeight * WireframeSupplierExample.FIELDS.length;
            ensure(tableHeight + LINE);
            float top = y;
            float bottom = top - tableHeight;
            stream.setStrokingColor(0.2f);
            stream.addRect(MARGIN, bottom, tableWidth, tableHeight);
            stream.stroke();
            float labelX = MARGIN + groupWidth;
            float valueX = labelX + labelWidth;
            stream.moveTo(labelX, bottom);
            stream.lineTo(labelX, top);
            stream.stroke();
            stream.moveTo(valueX, bottom);
            stream.lineTo(valueX, top);
            stream.stroke();
            float rowY = top;
            for (int i = 0; i < WireframeSupplierExample.FIELDS.length; i++) {
                String[] field = WireframeSupplierExample.FIELDS[i];
                rowY -= rowHeight;
                if (i > 0) {
                    stream.moveTo(labelX, rowY + rowHeight);
                    stream.lineTo(MARGIN + tableWidth, rowY + rowHeight);
                    stream.stroke();
                }
                drawText(labelX + CELL_PADDING, rowY + 5, field[0], BODY_SIZE);
                List<String> lines = wrap(field[1], valueWidth - (CELL_PADDING * 2), BODY_SIZE);
                drawText(valueX + CELL_PADDING, rowY + 5, lines.get(0), BODY_SIZE);
            }
            float labelCenterY = (top + bottom) / 2f - 3f;
            drawText(MARGIN + CELL_PADDING, labelCenterY, "공급자", BODY_SIZE);
            y = bottom - LINE * 0.4f;
        }

        private void paintFormalTableHeader() throws IOException {
            float height = FORMAL_CELL_TOP + FORMAL_CELL_BOTTOM;
            ensure(height + 2);
            float top = y;
            float baseline = top - FORMAL_CELL_TOP;
            String[] headers = {"NO", "제품명", "제품코드", "제조사", "제품가격", "수량", "금액"};
            Align[] aligns = {
                    Align.CENTER, Align.CENTER, Align.CENTER, Align.CENTER, Align.CENTER, Align.CENTER, Align.CENTER
            };
            for (int i = 0; i < headers.length; i++) {
                drawCell(formalColumnX(i), formalColumns[i], baseline, headers[i], aligns[i]);
            }
            y = top - height;
            rule(y);
        }

        private void paintFormalItem(int number, AdminQuoteItemResponse item) throws IOException {
            List<String> name = wrap(text(item.productName()), formalColumns[1] - (CELL_PADDING * 2), BODY_SIZE);
            List<String> code = wrap(text(item.productCode()), formalColumns[2] - (CELL_PADDING * 2), BODY_SIZE);
            List<String> manufacturer = wrap(
                    WireframeSupplierExample.MANUFACTURER,
                    formalColumns[3] - (CELL_PADDING * 2),
                    BODY_SIZE
            );
            int rows = Math.max(1, Math.max(name.size(), Math.max(code.size(), manufacturer.size())));
            float height = FORMAL_CELL_TOP + (LINE * Math.max(rows - 1, 0)) + FORMAL_CELL_BOTTOM;
            ensure(height + 2);
            float top = y;
            float baseline = top - FORMAL_CELL_TOP;
            drawLines(formalColumnX(0), formalColumns[0], baseline, List.of(Integer.toString(number)), Align.CENTER);
            drawLines(formalColumnX(1), formalColumns[1], baseline, name, Align.LEFT);
            drawLines(formalColumnX(2), formalColumns[2], baseline, code, Align.LEFT);
            drawLines(formalColumnX(3), formalColumns[3], baseline, manufacturer, Align.CENTER);
            drawLines(formalColumnX(4), formalColumns[4], baseline, List.of(won(item.salePrice())), Align.RIGHT);
            drawLines(
                    formalColumnX(5),
                    formalColumns[5],
                    baseline,
                    List.of(Integer.toString(item.quantity())),
                    Align.CENTER
            );
            drawLines(formalColumnX(6), formalColumns[6], baseline, List.of(won(item.amount())), Align.RIGHT);
            y = top - height;
            rule(y);
        }

        private float formalColumnX(int index) {
            float x = MARGIN;
            for (int i = 0; i < index; i++) {
                x += formalColumns[i];
            }
            return x;
        }

        private void field(String label, String value) throws IOException {
            List<String> lines = wrap(text(value), pageWidth - (MARGIN * 2) - LABEL_WIDTH, BODY_SIZE);
            ensure(LINE * lines.size());
            drawText(MARGIN, y, label, BODY_SIZE);
            for (String line : lines) {
                drawText(MARGIN + LABEL_WIDTH, y, line, BODY_SIZE);
                y -= LINE;
            }
        }

        private void heading(String value) throws IOException {
            ensure(LINE);
            drawText(MARGIN, y, value, BODY_SIZE);
            y -= LINE;
        }

        private void paintItem(AdminQuoteItemResponse item) throws IOException {
            List<String> name = wrap(text(item.productName()), columns[0] - (CELL_PADDING * 2), BODY_SIZE);
            List<String> code = wrap(text(item.productCode()), columns[1] - (CELL_PADDING * 2), BODY_SIZE);
            int rows = Math.max(1, Math.max(name.size(), code.size()));
            float height = (LINE * rows) + 6;
            ensure(height);
            float baseline = y;
            drawLines(MARGIN, columns[0], baseline, name, Align.LEFT);
            drawLines(columnX(1), columns[1], baseline, code, Align.LEFT);
            drawLines(columnX(2), columns[2], baseline, List.of(won(item.salePrice())), Align.RIGHT);
            drawLines(columnX(3), columns[3], baseline, List.of(Integer.toString(item.quantity())), Align.RIGHT);
            drawLines(columnX(4), columns[4], baseline, List.of(won(item.amount())), Align.RIGHT);
            y -= height;
            rule(y + 4);
        }

        private void paintTableHeader(String priceLabel) throws IOException {
            this.priceLabel = priceLabel;
            float baseline = y;
            drawCell(MARGIN, columns[0], baseline, "제품명", Align.LEFT);
            drawCell(columnX(1), columns[1], baseline, "제품 코드", Align.LEFT);
            drawCell(columnX(2), columns[2], baseline, priceLabel, Align.RIGHT);
            drawCell(columnX(3), columns[3], baseline, "수량", Align.RIGHT);
            drawCell(columnX(4), columns[4], baseline, "금액", Align.RIGHT);
            y -= LINE + 6;
            rule(y + 4);
        }

        private void total(String label, BigDecimal amount) throws IOException {
            String value = label + "    " + won(amount);
            float width = stringWidth(value, BODY_SIZE);
            drawText(pageWidth - MARGIN - width, y, value, BODY_SIZE);
            y -= LINE;
        }

        private void drawLines(float x, float width, float baseline, List<String> lines, Align align) throws IOException {
            float lineY = baseline;
            for (String line : lines) {
                drawCell(x, width, lineY, line, align);
                lineY -= LINE;
            }
        }

        private void drawCell(float x, float width, float baseline, String value, Align align) throws IOException {
            float textWidth = stringWidth(value, BODY_SIZE);
            float usable = Math.max(0f, width - (CELL_PADDING * 2));
            float drawX;
            if (align == Align.RIGHT) {
                drawX = x + width - CELL_PADDING - Math.min(textWidth, usable);
            } else if (align == Align.CENTER) {
                drawX = x + Math.max(CELL_PADDING, (width - Math.min(textWidth, usable)) / 2f);
            } else {
                drawX = x + CELL_PADDING;
            }
            drawText(drawX, baseline, value, BODY_SIZE);
        }

        private void drawCentered(String value, float size) throws IOException {
            float width = stringWidth(value, size);
            drawText((pageWidth - width) / 2f, y, value, size);
            y -= LINE;
        }

        private void drawText(float x, float baseline, String value, float size) throws IOException {
            if (value == null || value.isEmpty()) {
                return;
            }
            stream.setFont(font, size);
            stream.beginText();
            stream.newLineAtOffset(x, baseline);
            stream.showText(value);
            stream.endText();
        }

        private void rule(float baseline) throws IOException {
            stream.moveTo(MARGIN, baseline);
            stream.lineTo(pageWidth - MARGIN, baseline);
            stream.stroke();
        }

        private void ensure(float needed) throws IOException {
            if (y - needed >= MARGIN) {
                return;
            }
            newPage();
        }

        private void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = pageHeight - MARGIN;
            if (repeatTableHeader) {
                if (formalMode) {
                    paintFormalTableHeader();
                } else {
                    paintTableHeader(priceLabel);
                }
            }
        }

        private float columnX(int index) {
            float x = MARGIN;
            for (int i = 0; i < index; i++) {
                x += columns[i];
            }
            return x;
        }

        private List<String> wrap(String value, float maxWidth, float size) throws IOException {
            if (value.isEmpty()) {
                return List.of("");
            }
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                if (character == '\n' || character == '\r') {
                    lines.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(character);
                if (stringWidth(current.toString(), size) > maxWidth) {
                    current.deleteCharAt(current.length() - 1);
                    if (current.isEmpty()) {
                        lines.add(String.valueOf(character));
                    } else {
                        lines.add(current.toString());
                        current.setLength(0);
                        current.append(character);
                    }
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        private float stringWidth(String value, float size) throws IOException {
            if (value == null || value.isEmpty()) {
                return 0f;
            }
            return font.getStringWidth(value) / 1000f * size;
        }

        @Override
        public void close() throws IOException {
            closeStream();
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
