package net.likelion.bebc25.quoteon.member;

public record Member(
        long id,
        String email,
        String passwordHash,
        String name,
        String phone,
        String companyName,
        String role,
        String status
) {
}
