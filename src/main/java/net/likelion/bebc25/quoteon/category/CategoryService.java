package net.likelion.bebc25.quoteon.category;

import net.likelion.bebc25.quoteon.category.dto.CategoryResponse;
import net.likelion.bebc25.quoteon.category.dto.SaveCategoryRequest;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll();
    }

    public CategoryResponse create(SaveCategoryRequest request) {
        String name = normalizeName(request.name());
        requireParent(request.parentId());
        requireUniqueName(name, null);
        long id = categoryRepository.insert(name, request.parentId());
        return categoryRepository.findById(id).orElseThrow(this::missingCategory);
    }

    public CategoryResponse update(long categoryId, SaveCategoryRequest request) {
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw missingCategory();
        }
        String name = normalizeName(request.name());
        requireParent(request.parentId());
        requireNoCycle(categoryId, request.parentId());
        requireUniqueName(name, categoryId);
        int updated = categoryRepository.update(categoryId, name, request.parentId());
        if (updated != 1) {
            throw missingCategory();
        }
        return categoryRepository.findById(categoryId).orElseThrow(this::missingCategory);
    }

    public CategoryResponse delete(long categoryId) {
        CategoryResponse category = categoryRepository.findById(categoryId).orElseThrow(this::missingCategory);
        if (categoryRepository.countChildren(categoryId) > 0) {
            throw new CategoryHasChildrenException();
        }
        if (categoryRepository.countProducts(categoryId) > 0) {
            throw new CategoryHasProductsException();
        }
        int deleted = categoryRepository.delete(categoryId);
        if (deleted != 1) {
            throw missingCategory();
        }
        return category;
    }

    private void requireParent(Long parentId) {
        if (parentId == null) {
            return;
        }
        if (categoryRepository.findById(parentId).isEmpty()) {
            throw new CategoryNotFoundException("상위 카테고리를 찾을 수 없습니다.");
        }
    }

    private void requireNoCycle(long categoryId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId == categoryId) {
            throw new CategoryParentInvalidException("자기 자신을 상위 카테고리로 지정할 수 없습니다.");
        }
        Set<Long> seen = new HashSet<>();
        Long current = parentId;
        while (current != null) {
            if (!seen.add(current)) {
                break;
            }
            if (current == categoryId) {
                throw new CategoryParentInvalidException("하위 카테고리를 상위 카테고리로 지정할 수 없습니다.");
            }
            current = categoryRepository.findParentId(current);
        }
    }

    private void requireUniqueName(String name, Long excludeId) {
        categoryRepository.findIdByName(name).ifPresent(existingId -> {
            if (excludeId == null || !existingId.equals(excludeId)) {
                throw new CategoryNameExistsException();
            }
        });
    }

    private static String normalizeName(String name) {
        return name.trim();
    }

    private CategoryNotFoundException missingCategory() {
        return new CategoryNotFoundException("카테고리를 찾을 수 없습니다.");
    }
}
