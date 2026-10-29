package net.likelion.bebc25.quoteon.customer.dto;

public record CustomerSummaryResponse(
        long id,
        String name,
        String phone,
        String customerType
) {
}
