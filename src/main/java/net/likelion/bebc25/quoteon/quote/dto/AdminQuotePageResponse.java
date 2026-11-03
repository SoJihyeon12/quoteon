package net.likelion.bebc25.quoteon.quote.dto;

import java.util.List;

public record AdminQuotePageResponse(
        List<AdminQuoteSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
