package net.likelion.bebc25.quoteon.member;

public record MemberSummary(
        long id,
        String name,
        String email,
        String phone,
        String companyName,
        String role,
        String status
) {
}
