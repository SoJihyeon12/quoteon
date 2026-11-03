package net.likelion.bebc25.quoteon.admin.dto;

public record MemberQuoteRankResponse(
        long memberId,
        String memberName,
        long quoteCount
) {
}
