package net.likelion.bebc25.quoteon.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record DeactivateMemberRequest(
        @NotBlank(message = "비활성화 사유를 선택해 주세요.")
        String reasonCode,
        String reasonDetail
) {
}
