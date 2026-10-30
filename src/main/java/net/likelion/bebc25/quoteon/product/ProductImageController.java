package net.likelion.bebc25.quoteon.product;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductImageController {

    private final ProductImageStorage productImageStorage;

    public ProductImageController(ProductImageStorage productImageStorage) {
        this.productImageStorage = productImageStorage;
    }

    @GetMapping("/images/products/{filename:.+}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        ProductImageStorage.LoadedImage image = productImageStorage.load(filename);
        return ResponseEntity.ok()
                .contentType(image.mediaType())
                .body(image.bytes());
    }
}
