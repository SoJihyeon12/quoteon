package net.likelion.bebc25.quoteon.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SaveProductRequest(
        @NotNull(message = "카테고리를 선택해 주세요.")
        Long categoryId,
        @NotBlank(message = "제품명을 입력해 주세요.")
        @Size(max = 255, message = "제품명은 255자 이하로 입력해 주세요.")
        String name,
        @NotBlank(message = "제품 코드를 입력해 주세요.")
        @Size(max = 100, message = "제품 코드는 100자 이하로 입력해 주세요.")
        String productCode,
        @NotNull(message = "단가를 입력해 주세요.")
        @Digits(integer = 10, fraction = 2, message = "단가는 소수점 둘째 자리까지, 정수 10자리 이하로 입력해 주세요.")
        BigDecimal unitPrice,
        @NotNull(message = "판매 가격을 입력해 주세요.")
        @Digits(integer = 10, fraction = 2, message = "판매 가격은 소수점 둘째 자리까지, 정수 10자리 이하로 입력해 주세요.")
        BigDecimal salePrice,
        @Size(max = 500, message = "규격은 500자 이하로 입력해 주세요.")
        String specification,
        String description
) {
}
