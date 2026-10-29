package net.likelion.bebc25.quoteon.member;

import java.time.LocalDateTime;

public record PendingMember(
        long id,
        String name,
        String email,
        String phone,
        String companyName,
        String role,
        LocalDateTime createdAt
) {
}
