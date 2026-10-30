package net.likelion.bebc25.quoteon.product.dto;

public record DetailImageUploadResponse(
        Long productId,
        String imagePath,
        int sortOrder
) {
}
