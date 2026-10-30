package net.likelion.bebc25.quoteon.admin.dto;

import net.likelion.bebc25.quoteon.member.MemberSummary;

public record MemberSummaryResponse(
        long id,
        String name,
        String email,
        String phone,
        String companyName,
        String role,
        String status
) {

    public static MemberSummaryResponse from(MemberSummary member) {
        return new MemberSummaryResponse(
                member.id(),
                member.name(),
                member.email(),
                member.phone(),
                member.companyName(),
                member.role(),
                member.status()
        );
    }
}
