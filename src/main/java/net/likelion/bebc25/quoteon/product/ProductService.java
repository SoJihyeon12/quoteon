package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.category.CategoryNotFoundException;
import net.likelion.bebc25.quoteon.category.CategoryRepository;
import net.likelion.bebc25.quoteon.product.dto.AdminProductDetailResponse;
import net.likelion.bebc25.quoteon.product.dto.AdminProductPageResponse;
import net.likelion.bebc25.quoteon.product.dto.AdminProductSummaryResponse;
import net.likelion.bebc25.quoteon.product.dto.ProductResponse;
import net.likelion.bebc25.quoteon.product.dto.ProductDetailImageResponse;
import net.likelion.bebc25.quoteon.product.dto.SaveProductRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductImageRepository productImageRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productImageRepository = productImageRepository;
    }

    public List<ProductResponse> getProducts(String keyword) {
        return productRepository.findOnSale(normalizeKeyword(keyword));
    }

    public AdminProductPageResponse getAdminProducts(
            String keyword,
            Long categoryId,
            BigDecimal salePrice,
            String status,
            int page,
            int size
    ) {
        if (page < DEFAULT_PAGE) {
            throw new InvalidProductQueryException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidProductQueryException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeKeyword(status);
        long totalElements = productRepository.countForAdmin(
                normalizedKeyword,
                categoryId,
                salePrice,
                normalizedStatus
        );
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<AdminProductSummaryResponse> items = productRepository.findForAdmin(
                normalizedKeyword,
                categoryId,
                salePrice,
                normalizedStatus,
                size,
                (long) page * size
        );
        return new AdminProductPageResponse(items, page, size, totalElements, totalPages);
    }

    public AdminProductDetailResponse getAdminProduct(long productId) {
        AdminProductDetailResponse detail = productRepository.findAdminDetailById(productId)
                .orElseThrow(ProductNotFoundException::new);
        return detail.withDetailImages(productImageRepository.findByProductId(productId));
    }

    public AdminProductDetailResponse create(SaveProductRequest request) {
        requireCategory(request.categoryId());
        String name = request.name().trim();
        String productCode = request.productCode().trim();
        requireUniqueCode(productCode, null);
        long id = productRepository.insert(
                request.categoryId(),
                productCode,
                name,
                request.unitPrice(),
                request.salePrice(),
                blankToNull(request.specification()),
                blankToNull(request.description())
        );
        return getAdminProduct(id);
    }

    public AdminProductDetailResponse update(long productId, SaveProductRequest request) {
        if (productRepository.findAdminDetailById(productId).isEmpty()) {
            throw new ProductNotFoundException();
        }
        requireCategory(request.categoryId());
        String name = request.name().trim();
        String productCode = request.productCode().trim();
        requireUniqueCode(productCode, productId);
        int updated = productRepository.update(
                productId,
                request.categoryId(),
                productCode,
                name,
                request.unitPrice(),
                request.salePrice(),
                blankToNull(request.specification()),
                blankToNull(request.description())
        );
        if (updated != 1) {
            throw new ProductNotFoundException();
        }
        return getAdminProduct(productId);
    }

    public AdminProductDetailResponse stopSale(long productId) {
        int updated = productRepository.updateStatusIfCurrent(
                productId,
                ProductRepository.STOPPED_STATUS,
                ProductRepository.ON_SALE_STATUS
        );
        if (updated != 1) {
            if (productRepository.findRepresentative(productId).isEmpty()) {
                throw new ProductNotFoundException();
            }
            throw new ProductStatusConflictException("판매 중인 제품만 판매 중지할 수 있습니다.");
        }
        return getAdminProduct(productId);
    }

    @Transactional
    public ProductDeletion delete(long productId) {
        AdminProductDetailResponse detail = getAdminProduct(productId);
        if (productRepository.countQuoteItems(productId) > 0) {
            throw new ProductInUseException();
        }
        List<String> imagePaths = imagePathsOf(detail);
        try {
            productImageRepository.deleteByProductId(productId);
            int deleted = productRepository.deleteById(productId);
            if (deleted != 1) {
                throw new ProductNotFoundException();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ProductInUseException();
        }
        return new ProductDeletion(detail, imagePaths);
    }

    private static List<String> imagePathsOf(AdminProductDetailResponse detail) {
        List<String> paths = new ArrayList<>();
        if (detail.imagePath() != null) {
            paths.add(detail.imagePath());
        }
        for (ProductDetailImageResponse image : detail.detailImages()) {
            if (image.imagePath() != null) {
                paths.add(image.imagePath());
            }
        }
        return List.copyOf(paths);
    }

    private void requireCategory(long categoryId) {
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw new CategoryNotFoundException("카테고리를 찾을 수 없습니다.");
        }
    }

    private void requireUniqueCode(String productCode, Long excludeId) {
        productRepository.findIdByProductCode(productCode).ifPresent(existingId -> {
            if (excludeId == null || !existingId.equals(excludeId)) {
                throw new ProductCodeExistsException();
            }
        });
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
