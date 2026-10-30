package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.product.dto.AdminProductDetailResponse;
import net.likelion.bebc25.quoteon.product.dto.DetailImageUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageService {

    private static final String SLOT_MESSAGE = "상세 이미지 슬롯은 1, 2, 3만 선택할 수 있습니다.";

    private final ProductRepository productRepository;
    private final ProductImageValidator productImageValidator;
    private final ProductImageStorage productImageStorage;
    private final ProductImageTx productImageTx;
    private final ProductService productService;

    public ProductImageService(
            ProductRepository productRepository,
            ProductImageValidator productImageValidator,
            ProductImageStorage productImageStorage,
            ProductImageTx productImageTx,
            ProductService productService
    ) {
        this.productRepository = productRepository;
        this.productImageValidator = productImageValidator;
        this.productImageStorage = productImageStorage;
        this.productImageTx = productImageTx;
        this.productService = productService;
    }

    public AdminProductDetailResponse uploadRepresentative(long productId, MultipartFile file) {
        requireProduct(productId);
        ProductImageStorage.StoredImage stored = productImageStorage.store(productImageValidator.validate(file));
        String previous = saveRepresentative(productId, stored.publicPath());
        productImageStorage.deletePublicPath(previous);
        return productService.getAdminProduct(productId);
    }

    public DetailImageUploadResponse uploadDetail(long productId, int slot, MultipartFile file) {
        if (slot < 1 || slot > 3) {
            throw new InvalidImageSlotException(SLOT_MESSAGE);
        }
        requireProduct(productId);
        ProductImageStorage.StoredImage stored = productImageStorage.store(productImageValidator.validate(file));
        saveDetail(productId, slot, stored.publicPath());
        return new DetailImageUploadResponse(productId, stored.publicPath(), slot);
    }

    private String saveRepresentative(long productId, String publicPath) {
        try {
            return productImageTx.replaceRepresentative(productId, publicPath);
        } catch (RuntimeException exception) {
            productImageStorage.deletePublicPath(publicPath);
            throw exception;
        }
    }

    private void saveDetail(long productId, int slot, String publicPath) {
        String previous;
        try {
            previous = productImageTx.replaceDetail(productId, slot, publicPath);
        } catch (RuntimeException exception) {
            productImageStorage.deletePublicPath(publicPath);
            throw exception;
        }
        productImageStorage.deletePublicPath(previous);
    }

    private void requireProduct(long productId) {
        if (productRepository.findRepresentative(productId).isEmpty()) {
            throw new ProductNotFoundException();
        }
    }
}
