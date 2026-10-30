package net.likelion.bebc25.quoteon.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCategoryRequest(
        @NotBlank(message = "카테고리명을 입력해 주세요.")
        @Size(max = 100, message = "카테고리명은 100자 이하로 입력해 주세요.")
        String name,
        Long parentId
) {
}
