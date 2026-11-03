package net.likelion.bebc25.quoteon.admin.dto;

import java.math.BigDecimal;

public record PopularProductResponse(
        String productCode,
        long quoteCount,
        BigDecimal ratio
) {
}
