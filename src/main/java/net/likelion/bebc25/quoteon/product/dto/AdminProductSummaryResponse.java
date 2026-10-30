package net.likelion.bebc25.quoteon.product.dto;

import java.math.BigDecimal;

public record AdminProductSummaryResponse(
        Long id,
        String imagePath,
        String name,
        String productCode,
        BigDecimal salePrice,
        String status
) {
}
