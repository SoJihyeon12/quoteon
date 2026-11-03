package net.likelion.bebc25.quoteon.product.dto;

public record FavoriteResponse(
        Long productId,
        boolean favorited
) {
}
