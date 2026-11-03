package net.likelion.bebc25.quoteon.quote;

import java.time.LocalDateTime;

record QuoteEmailHistory(
        long id,
        String recipientEmail,
        String subject,
        String status,
        String failureReason,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {
}
