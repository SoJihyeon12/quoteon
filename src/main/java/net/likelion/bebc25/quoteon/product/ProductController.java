package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.product.dto.FavoriteResponse;
import net.likelion.bebc25.quoteon.product.dto.ProductPageResponse;
import net.likelion.bebc25.quoteon.product.dto.SalesProductDetailResponse;
import net.likelion.bebc25.quoteon.security.MemberPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductFavoriteService productFavoriteService;

    public ProductController(ProductService productService, ProductFavoriteService productFavoriteService) {
        this.productService = productService;
        this.productFavoriteService = productFavoriteService;
    }

    @GetMapping
    public ProductPageResponse getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean favoriteOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return productService.getProducts(currentMemberId(), keyword, categoryId, favoriteOnly, page, size);
    }

    @GetMapping("/{productId}")
    public SalesProductDetailResponse getProduct(@PathVariable long productId) {
        return productService.getOnSaleProduct(currentMemberId(), productId);
    }

    @PostMapping("/{productId}/favorite")
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteResponse addFavorite(@PathVariable long productId) {
        return productFavoriteService.add(currentMemberId(), productId);
    }

    @DeleteMapping("/{productId}/favorite")
    public FavoriteResponse removeFavorite(@PathVariable long productId) {
        return productFavoriteService.remove(currentMemberId(), productId);
    }

    private long currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        return principal.id();
    }
}
