package net.likelion.bebc25.quoteon.quote.dto;

import java.math.BigDecimal;

public record AdminQuoteItemResponse(
        long id,
        String productName,
        String productCode,
        String specification,
        String specificationSource,
        BigDecimal salePrice,
        int quantity,
        BigDecimal amount
) {
}
