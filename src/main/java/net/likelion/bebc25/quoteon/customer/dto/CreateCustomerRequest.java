package net.likelion.bebc25.quoteon.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import net.likelion.bebc25.quoteon.customer.CustomerEmail;
import net.likelion.bebc25.quoteon.customer.CustomerType;

public record CreateCustomerRequest(
        @NotNull(message = "고객명을 입력해 주세요.")
        @Size(max = 100, message = "고객명은 100자 이하로 입력해 주세요.")
        String name,
        @NotNull(message = "고객 유형을 선택해 주세요.")
        @CustomerType
        String customerType,
        @Size(max = 20, message = "연락처는 20자 이하로 입력해 주세요.")
        String phone,
        @CustomerEmail
        String email,
        @Size(max = 500, message = "주소는 500자 이하로 입력해 주세요.")
        String address,
        String memo
) {
}
