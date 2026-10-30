package net.likelion.bebc25.quoteon.product.dto;

public record ProductDetailImageResponse(
        Long id,
        String imagePath,
        int sortOrder
) {
}
