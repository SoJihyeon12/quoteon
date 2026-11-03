package net.likelion.bebc25.quoteon.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminQuoteDetailResponse(
        long id,
        String quoteNumber,
        LocalDate quoteDate,
        String authorName,
        String status,
        String memo,
        BigDecimal supplyAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        AdminQuoteCustomerResponse customer,
        List<AdminQuoteItemResponse> items
) {
}
