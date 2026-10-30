package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.product.dto.AdminProductDetailResponse;

import java.util.List;

public record ProductDeletion(
        AdminProductDetailResponse detail,
        List<String> imagePaths
) {
}
