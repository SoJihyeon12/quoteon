package net.likelion.bebc25.quoteon.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record QuoteSummaryResponse(
        long id,
        String quoteNumber,
        long customerId,
        String customerName,
        LocalDate quoteDate,
        String status,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
}
