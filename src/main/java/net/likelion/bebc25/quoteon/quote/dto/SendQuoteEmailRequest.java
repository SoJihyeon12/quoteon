package net.likelion.bebc25.quoteon.quote.dto;

import jakarta.validation.constraints.NotBlank;
import net.likelion.bebc25.quoteon.customer.CustomerEmail;

public record SendQuoteEmailRequest(
        @NotBlank(message = "수신 이메일을 입력해 주세요.")
        @CustomerEmail
        String recipient,
        String subject,
        String content
) {
}
