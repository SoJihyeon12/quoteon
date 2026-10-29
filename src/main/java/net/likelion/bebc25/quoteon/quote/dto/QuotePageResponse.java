package net.likelion.bebc25.quoteon.quote.dto;

import java.util.List;

public record QuotePageResponse(
        List<QuoteSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
