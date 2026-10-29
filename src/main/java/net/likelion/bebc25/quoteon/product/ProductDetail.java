package net.likelion.bebc25.quoteon.product;

import java.math.BigDecimal;

public record ProductDetail(
        long id,
        String productCode,
        String name,
        BigDecimal salePrice,
        String status
) {
}
