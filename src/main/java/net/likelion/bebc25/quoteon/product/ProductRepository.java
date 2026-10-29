package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.product.dto.ProductResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ProductRepository {

    private static final String ON_SALE_STATUS = "ACTIVE";

    private static final RowMapper<ProductResponse> ROW_MAPPER = ProductRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductResponse> findOnSale(String keyword) {
        String sql = """
                SELECT p.id,
                       p.product_code,
                       p.name,
                       p.sale_price,
                       p.category_id,
                       c.name AS category_name,
                       p.image_path,
                       p.status
                FROM product p
                INNER JOIN category c ON c.id = p.category_id
                WHERE p.status = ?
                  AND (
                        ? = 0
                        OR p.name LIKE ? ESCAPE '\\\\'
                        OR p.product_code LIKE ? ESCAPE '\\\\'
                      )
                ORDER BY p.id
                """;
        boolean hasKeyword = keyword != null;
        String pattern = hasKeyword ? toContainsPattern(keyword) : "%";
        return jdbcTemplate.query(sql, ps -> {
            ps.setString(1, ON_SALE_STATUS);
            ps.setInt(2, hasKeyword ? 1 : 0);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
        }, ROW_MAPPER);
    }

    private static ProductResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ProductResponse(
                rs.getLong("id"),
                rs.getString("product_code"),
                rs.getString("name"),
                rs.getBigDecimal("sale_price"),
                rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getString("image_path"),
                rs.getString("status")
        );
    }

    private static String toContainsPattern(String keyword) {
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
