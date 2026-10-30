package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.product.dto.AdminProductDetailResponse;
import org.springframework.stereotype.Service;

@Service
public class ProductRemovalService {

    private final ProductService productService;
    private final ProductImageStorage productImageStorage;

    public ProductRemovalService(ProductService productService, ProductImageStorage productImageStorage) {
        this.productService = productService;
        this.productImageStorage = productImageStorage;
    }

    public AdminProductDetailResponse delete(long productId) {
        ProductDeletion deletion = productService.delete(productId);
        for (String imagePath : deletion.imagePaths()) {
            productImageStorage.deletePublicPath(imagePath);
        }
        return deletion.detail();
    }
}
