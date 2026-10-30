package net.likelion.bebc25.quoteon.admin.dto;

import java.util.List;

public record MemberSummaryPageResponse(
        List<MemberSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
