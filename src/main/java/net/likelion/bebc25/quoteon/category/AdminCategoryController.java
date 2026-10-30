package net.likelion.bebc25.quoteon.category;

import jakarta.validation.Valid;
import net.likelion.bebc25.quoteon.category.dto.CategoryResponse;
import net.likelion.bebc25.quoteon.category.dto.SaveCategoryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody SaveCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{categoryId}")
    public CategoryResponse update(
            @PathVariable long categoryId,
            @Valid @RequestBody SaveCategoryRequest request
    ) {
        return categoryService.update(categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    public CategoryResponse delete(@PathVariable long categoryId) {
        return categoryService.delete(categoryId);
    }
}
