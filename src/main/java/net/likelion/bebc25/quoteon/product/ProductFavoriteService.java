package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.product.dto.FavoriteResponse;
import org.springframework.stereotype.Service;

@Service
public class ProductFavoriteService {

    private final ProductFavoriteRepository productFavoriteRepository;
    private final ProductRepository productRepository;

    public ProductFavoriteService(
            ProductFavoriteRepository productFavoriteRepository,
            ProductRepository productRepository
    ) {
        this.productFavoriteRepository = productFavoriteRepository;
        this.productRepository = productRepository;
    }

    public FavoriteResponse add(long memberId, long productId) {
        ProductDetail product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
        if (!ProductRepository.ON_SALE_STATUS.equals(product.status())) {
            throw new FavoriteProductUnavailableException("판매 중인 제품만 즐겨찾기에 추가할 수 있습니다.");
        }
        if (productFavoriteRepository.exists(memberId, productId)) {
            throw new FavoriteAlreadyExistsException();
        }
        productFavoriteRepository.insert(memberId, productId);
        return new FavoriteResponse(productId, true);
    }

    public FavoriteResponse remove(long memberId, long productId) {
        if (productRepository.findById(productId).isEmpty()) {
            throw new ProductNotFoundException();
        }
        int deleted = productFavoriteRepository.delete(memberId, productId);
        if (deleted != 1) {
            throw new FavoriteNotFoundException();
        }
        return new FavoriteResponse(productId, false);
    }
}
