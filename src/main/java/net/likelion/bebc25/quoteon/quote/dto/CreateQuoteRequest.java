package net.likelion.bebc25.quoteon.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateQuoteRequest(
        @NotNull(message = "고객을 선택해 주세요.")
        Long customerId,
        @NotNull(message = "견적일을 입력해 주세요.")
        LocalDate quoteDate,
        @NotEmpty(message = "견적 항목을 한 개 이상 입력해 주세요.")
        @Valid
        List<CreateQuoteItemRequest> items
) {
}
