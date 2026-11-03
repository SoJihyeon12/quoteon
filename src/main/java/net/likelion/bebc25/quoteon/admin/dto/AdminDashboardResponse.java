package net.likelion.bebc25.quoteon.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminDashboardResponse(
        long todayQuoteCount,
        BigDecimal monthQuoteAmount,
        long pendingMemberCount,
        long activeMemberCount,
        List<PopularProductResponse> popularProducts,
        List<MemberQuoteRankResponse> memberQuoteRanks
) {
}
