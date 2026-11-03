package net.likelion.bebc25.quoteon.quote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record UpdateQuotePrivacyRequest(
        @NotNull
        @JsonProperty("isPrivate")
        Boolean isPrivate
) {
}
