package net.likelion.bebc25.quoteon.quote.dto;

import java.time.LocalDateTime;

public record QuoteEmailHistoryResponse(
        long id,
        String recipientEmail,
        String subject,
        String status,
        LocalDateTime sentAt,
        String failureReason,
        LocalDateTime createdAt
) {
}
