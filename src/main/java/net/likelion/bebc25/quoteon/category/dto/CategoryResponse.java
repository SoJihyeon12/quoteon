package net.likelion.bebc25.quoteon.category.dto;

public record CategoryResponse(
        Long id,
        String name,
        Long parentId
) {
}
