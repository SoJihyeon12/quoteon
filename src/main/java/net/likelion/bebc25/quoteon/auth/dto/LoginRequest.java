package net.likelion.bebc25.quoteon.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
        String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(max = 72, message = "비밀번호는 72자 이하로 입력해 주세요.")
        String password
) {
}
