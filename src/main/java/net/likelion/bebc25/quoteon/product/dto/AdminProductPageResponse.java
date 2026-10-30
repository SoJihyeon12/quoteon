package net.likelion.bebc25.quoteon.product.dto;

import java.util.List;

public record AdminProductPageResponse(
        List<AdminProductSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
