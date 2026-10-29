package net.likelion.bebc25.quoteon.customer.dto;

import java.util.List;

public record CustomerPageResponse(
        List<CustomerSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
