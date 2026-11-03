package net.likelion.bebc25.quoteon.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminProductDetailResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String productCode,
        BigDecimal unitPrice,
        BigDecimal salePrice,
        String specification,
        String description,
        String imagePath,
        String status,
        boolean usedInQuotes,
        List<ProductDetailImageResponse> detailImages
) {
    public AdminProductDetailResponse withDetailImages(List<ProductDetailImageResponse> detailImages) {
        return new AdminProductDetailResponse(
                id,
                categoryId,
                categoryName,
                name,
                productCode,
                unitPrice,
                salePrice,
                specification,
                description,
                imagePath,
                status,
                usedInQuotes,
                detailImages == null ? List.of() : List.copyOf(detailImages)
        );
    }

    public AdminProductDetailResponse withUsedInQuotes(boolean usedInQuotes) {
        return new AdminProductDetailResponse(
                id,
                categoryId,
                categoryName,
                name,
                productCode,
                unitPrice,
                salePrice,
                specification,
                description,
                imagePath,
                status,
                usedInQuotes,
                detailImages
        );
    }
}
