package net.likelion.bebc25.quoteon.quote;

import java.math.BigDecimal;

public record QuoteItemWrite(
        long productId,
        String productName,
        String productCode,
        String specification,
        String specificationSource,
        BigDecimal salePrice,
        int quantity,
        BigDecimal amount
) {
}
