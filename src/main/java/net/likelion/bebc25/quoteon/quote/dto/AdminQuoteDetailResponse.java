package net.likelion.bebc25.quoteon.quote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        @JsonProperty("isPrivate") boolean isPrivate,
        AdminQuoteCustomerResponse customer,
        List<AdminQuoteItemResponse> items
) {
}
