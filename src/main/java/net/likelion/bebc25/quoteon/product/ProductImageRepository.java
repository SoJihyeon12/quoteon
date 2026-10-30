package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.product.dto.ProductDetailImageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductImageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductImageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductDetailImageResponse> findByProductId(long productId) {
        return jdbcTemplate.query(
                """
                SELECT id, image_path, sort_order
                FROM product_image
                WHERE product_id = ?
                ORDER BY sort_order
                """,
                (rs, rowNum) -> new ProductDetailImageResponse(
                        rs.getLong("id"),
                        rs.getString("image_path"),
                        rs.getInt("sort_order")
                ),
                productId
        );
    }

    public Optional<String> findPath(long productId, int sortOrder) {
        List<String> paths = jdbcTemplate.query(
                """
                SELECT image_path
                FROM product_image
                WHERE product_id = ? AND sort_order = ?
                """,
                (rs, rowNum) -> rs.getString("image_path"),
                productId,
                sortOrder
        );
        return paths.stream().findFirst();
    }

    public void insert(long productId, String imagePath, int sortOrder) {
        jdbcTemplate.update(
                """
                INSERT INTO product_image (product_id, image_path, sort_order)
                VALUES (?, ?, ?)
                """,
                productId,
                imagePath,
                sortOrder
        );
    }

    public int deleteByProductId(long productId) {
        return jdbcTemplate.update("DELETE FROM product_image WHERE product_id = ?", productId);
    }

    public int updatePath(long productId, int sortOrder, String imagePath) {
        return jdbcTemplate.update(
                """
                UPDATE product_image
                SET image_path = ?
                WHERE product_id = ? AND sort_order = ?
                """,
                imagePath,
                productId,
                sortOrder
        );
    }
}
