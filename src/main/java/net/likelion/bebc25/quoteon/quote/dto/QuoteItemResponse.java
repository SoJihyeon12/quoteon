package net.likelion.bebc25.quoteon.quote.dto;

import java.math.BigDecimal;

public record QuoteItemResponse(
        long id,
        long productId,
        String productName,
        String productCode,
        BigDecimal salePrice,
        int quantity,
        BigDecimal amount
) {
}
