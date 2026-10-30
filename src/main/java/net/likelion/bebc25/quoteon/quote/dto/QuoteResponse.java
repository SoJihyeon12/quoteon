package net.likelion.bebc25.quoteon.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record QuoteResponse(
        long id,
        String quoteNumber,
        long customerId,
        String customerName,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String status,
        LocalDate quoteDate,
        LocalDateTime createdAt,
        List<QuoteItemResponse> items
) {
}
