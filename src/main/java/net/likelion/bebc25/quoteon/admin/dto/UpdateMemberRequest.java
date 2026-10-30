package net.likelion.bebc25.quoteon.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.likelion.bebc25.quoteon.member.MemberRole;

public record UpdateMemberRequest(
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 100, message = "이름은 100자 이하로 입력해 주세요.")
        String name,
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식을 확인해 주세요.")
        @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
        String email,
        @Size(max = 20, message = "휴대폰 번호는 20자 이하로 입력해 주세요.")
        String phone,
        @Size(max = 255, message = "회사명은 255자 이하로 입력해 주세요.")
        String companyName,
        @NotNull(message = "권한을 선택해 주세요.")
        @Pattern(
                regexp = MemberRole.USER + "|" + MemberRole.ADMIN,
                message = "권한은 영업사원 또는 관리자만 선택할 수 있습니다."
        )
        String role
) {
}
