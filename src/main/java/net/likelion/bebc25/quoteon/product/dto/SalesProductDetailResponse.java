package net.likelion.bebc25.quoteon.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalesProductDetailResponse(
        Long id,
        String productCode,
        String name,
        BigDecimal unitPrice,
        BigDecimal salePrice,
        String specification,
        String description,
        String imagePath,
        String status,
        boolean favorited,
        List<ProductDetailImageResponse> detailImages
) {
    public SalesProductDetailResponse withDetailImages(List<ProductDetailImageResponse> detailImages) {
        return new SalesProductDetailResponse(
                id,
                productCode,
                name,
                unitPrice,
                salePrice,
                specification,
                description,
                imagePath,
                status,
                favorited,
                detailImages == null ? List.of() : List.copyOf(detailImages)
        );
    }

    public SalesProductDetailResponse withFavorited(boolean favorited) {
        return new SalesProductDetailResponse(
                id,
                productCode,
                name,
                unitPrice,
                salePrice,
                specification,
                description,
                imagePath,
                status,
                favorited,
                detailImages
        );
    }
}
