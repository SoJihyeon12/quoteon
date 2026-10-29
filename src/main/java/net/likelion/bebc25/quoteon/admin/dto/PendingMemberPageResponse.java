package net.likelion.bebc25.quoteon.admin.dto;

import java.util.List;

public record PendingMemberPageResponse(
        List<PendingMemberResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
