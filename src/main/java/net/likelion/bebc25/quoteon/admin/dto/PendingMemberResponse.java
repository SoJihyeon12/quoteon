package net.likelion.bebc25.quoteon.admin.dto;

import net.likelion.bebc25.quoteon.member.PendingMember;

import java.time.LocalDateTime;

public record PendingMemberResponse(
        long id,
        String name,
        String email,
        String phone,
        String companyName,
        String role,
        LocalDateTime createdAt
) {

    public static PendingMemberResponse from(PendingMember member) {
        return new PendingMemberResponse(
                member.id(),
                member.name(),
                member.email(),
                member.phone(),
                member.companyName(),
                member.role(),
                member.createdAt()
        );
    }
}
