package net.likelion.bebc25.quoteon.quote.dto;

import java.util.List;

public record QuoteEmailHistoryListResponse(
        List<QuoteEmailHistoryResponse> items
) {
}
