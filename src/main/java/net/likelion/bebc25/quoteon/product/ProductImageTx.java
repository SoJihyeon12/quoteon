package net.likelion.bebc25.quoteon.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductImageTx {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    public ProductImageTx(ProductRepository productRepository, ProductImageRepository productImageRepository) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
    }

    @Transactional
    public String replaceRepresentative(long productId, String imagePath) {
        ProductRepository.StoredRepresentative current = productRepository.findRepresentative(productId)
                .orElseThrow(ProductNotFoundException::new);
        int updated = productRepository.updateImagePath(productId, imagePath);
        if (updated != 1) {
            throw new ProductNotFoundException();
        }
        return current.imagePath();
    }

    @Transactional
    public String replaceDetail(long productId, int sortOrder, String imagePath) {
        if (productRepository.findRepresentative(productId).isEmpty()) {
            throw new ProductNotFoundException();
        }
        String previous = productImageRepository.findPath(productId, sortOrder).orElse(null);
        if (previous == null) {
            productImageRepository.insert(productId, imagePath, sortOrder);
            return null;
        }
        int updated = productImageRepository.updatePath(productId, sortOrder, imagePath);
        if (updated != 1) {
            throw new ProductNotFoundException();
        }
        return previous;
    }
}
