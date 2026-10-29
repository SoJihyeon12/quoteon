package net.likelion.bebc25.quoteon.auth.dto;

import net.likelion.bebc25.quoteon.member.Member;

public record MemberResponse(
        long id,
        String email,
        String name,
        String phone,
        String companyName,
        String role,
        String status
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.id(),
                member.email(),
                member.name(),
                member.phone(),
                member.companyName(),
                member.role(),
                member.status()
        );
    }
}
