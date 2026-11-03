package net.likelion.bebc25.quoteon.quote.dto;

public record AdminQuoteCustomerResponse(
        long id,
        String name,
        String customerType,
        String phone,
        String email,
        String address
) {
}
