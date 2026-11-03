package net.likelion.bebc25.quoteon.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminQuoteSummaryResponse(
        long id,
        LocalDate quoteDate,
        String authorName,
        String customerName,
        BigDecimal totalAmount,
        String status
) {
}
