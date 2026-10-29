package net.likelion.bebc25.quoteon.product.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String productCode,
        String name,
        BigDecimal salePrice,
        Long categoryId,
        String categoryName,
        String imagePath,
        String status
) {
}
