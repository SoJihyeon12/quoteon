package net.likelion.bebc25.quoteon.customer;

public record CustomerRef(
        long id,
        long memberId,
        String status
) {
}
