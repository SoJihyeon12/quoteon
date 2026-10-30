package net.likelion.bebc25.quoteon.product;

import jakarta.validation.Valid;
import net.likelion.bebc25.quoteon.product.dto.AdminProductDetailResponse;
import net.likelion.bebc25.quoteon.product.dto.AdminProductPageResponse;
import net.likelion.bebc25.quoteon.product.dto.DetailImageUploadResponse;
import net.likelion.bebc25.quoteon.product.dto.SaveProductRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;
    private final ProductRemovalService productRemovalService;

    public AdminProductController(
            ProductService productService,
            ProductImageService productImageService,
            ProductRemovalService productRemovalService
    ) {
        this.productService = productService;
        this.productImageService = productImageService;
        this.productRemovalService = productRemovalService;
    }

    @GetMapping
    public AdminProductPageResponse getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal salePrice,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return productService.getAdminProducts(keyword, categoryId, salePrice, status, page, size);
    }

    @GetMapping("/{productId}")
    public AdminProductDetailResponse getProduct(@PathVariable long productId) {
        return productService.getAdminProduct(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductDetailResponse create(@Valid @RequestBody SaveProductRequest request) {
        return productService.create(request);
    }

    @PostMapping(value = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminProductDetailResponse uploadRepresentative(
            @PathVariable long productId,
            @RequestParam("file") MultipartFile file
    ) {
        return productImageService.uploadRepresentative(productId, file);
    }

    @PostMapping(value = "/{productId}/detail-images/{slot}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DetailImageUploadResponse uploadDetail(
            @PathVariable long productId,
            @PathVariable int slot,
            @RequestParam("file") MultipartFile file
    ) {
        return productImageService.uploadDetail(productId, slot, file);
    }

    @PostMapping("/{productId}/stop")
    public AdminProductDetailResponse stopSale(@PathVariable long productId) {
        return productService.stopSale(productId);
    }

    @DeleteMapping("/{productId}")
    public AdminProductDetailResponse delete(@PathVariable long productId) {
        return productRemovalService.delete(productId);
    }

    @PutMapping("/{productId}")
    public AdminProductDetailResponse update(
            @PathVariable long productId,
            @Valid @RequestBody SaveProductRequest request
    ) {
        return productService.update(productId, request);
    }
}
