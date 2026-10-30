package net.likelion.bebc25.quoteon.customer.dto;

public record CustomerResponse(
        long id,
        String name,
        String customerType,
        String phone,
        String email,
        String address,
        String memo
) {
}
