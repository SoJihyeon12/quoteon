package net.likelion.bebc25.quoteon.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.likelion.bebc25.quoteon.member.AccountType;

public record SignupRequest(
        @NotNull(message = "가입 구분을 선택해 주세요.")
        AccountType accountType,
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 100, message = "이름은 100자 이하로 입력해 주세요.")
        String name,
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식을 확인해 주세요.")
        @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
        String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 72, message = "비밀번호는 72자 이하로 입력해 주세요.")
        String password,
        @Size(max = 20, message = "휴대폰 번호는 20자 이하로 입력해 주세요.")
        String phone,
        @Size(max = 255, message = "회사명은 255자 이하로 입력해 주세요.")
        String companyName
) {
}
