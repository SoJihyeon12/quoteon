package net.likelion.bebc25.quoteon.product;

import net.likelion.bebc25.quoteon.product.dto.AdminProductDetailResponse;
import net.likelion.bebc25.quoteon.product.dto.AdminProductSummaryResponse;
import net.likelion.bebc25.quoteon.product.dto.ProductResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    public static final String ON_SALE_STATUS = "ACTIVE";
    public static final String STOPPED_STATUS = "INACTIVE";

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

    public Optional<ProductDetail> findById(long id) {
        String sql = """
                SELECT id, product_code, name, sale_price, status
                FROM product
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, id), ProductRepository::mapDetail).stream().findFirst();
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

    private static ProductDetail mapDetail(ResultSet rs, int rowNum) throws SQLException {
        return new ProductDetail(
                rs.getLong("id"),
                rs.getString("product_code"),
                rs.getString("name"),
                rs.getBigDecimal("sale_price"),
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

    public List<AdminProductSummaryResponse> findForAdmin(
            String keyword,
            Long categoryId,
            BigDecimal salePrice,
            String status,
            int limit,
            long offset
    ) {
        String sql = """
                SELECT p.id, p.image_path, p.name, p.product_code, p.sale_price, p.status
                FROM product p
                WHERE (
                        ? = 0
                        OR p.name LIKE ? ESCAPE '\\\\'
                        OR p.product_code LIKE ? ESCAPE '\\\\'
                        OR p.specification LIKE ? ESCAPE '\\\\'
                        OR p.description LIKE ? ESCAPE '\\\\'
                      )
                  AND (? = 0 OR p.category_id = ?)
                  AND (? = 0 OR p.sale_price = ?)
                  AND (? = 0 OR p.status = ?)
                ORDER BY p.id
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, ps -> {
            int next = bindAdminFilter(ps, keyword, categoryId, salePrice, status);
            ps.setInt(next, limit);
            ps.setLong(next + 1, offset);
        }, ADMIN_SUMMARY_MAPPER);
    }

    public long countForAdmin(String keyword, Long categoryId, BigDecimal salePrice, String status) {
        String sql = """
                SELECT COUNT(*)
                FROM product p
                WHERE (
                        ? = 0
                        OR p.name LIKE ? ESCAPE '\\\\'
                        OR p.product_code LIKE ? ESCAPE '\\\\'
                        OR p.specification LIKE ? ESCAPE '\\\\'
                        OR p.description LIKE ? ESCAPE '\\\\'
                      )
                  AND (? = 0 OR p.category_id = ?)
                  AND (? = 0 OR p.sale_price = ?)
                  AND (? = 0 OR p.status = ?)
                """;
        Long count = jdbcTemplate.query(sql, ps -> bindAdminFilter(ps, keyword, categoryId, salePrice, status), rs -> {
            rs.next();
            return rs.getLong(1);
        });
        return count == null ? 0L : count;
    }

    public Optional<AdminProductDetailResponse> findAdminDetailById(long id) {
        String sql = """
                SELECT p.id,
                       p.category_id,
                       c.name AS category_name,
                       p.name,
                       p.product_code,
                       p.unit_price,
                       p.sale_price,
                       p.specification,
                       p.description,
                       p.image_path,
                       p.status
                FROM product p
                INNER JOIN category c ON c.id = p.category_id
                WHERE p.id = ?
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, id), ADMIN_DETAIL_MAPPER).stream().findFirst();
    }

    public Optional<StoredRepresentative> findRepresentative(long id) {
        List<StoredRepresentative> rows = jdbcTemplate.query(
                "SELECT image_path FROM product WHERE id = ?",
                (rs, rowNum) -> new StoredRepresentative(rs.getString("image_path")),
                id
        );
        return rows.stream().findFirst();
    }

    public record StoredRepresentative(String imagePath) {
    }

    public int updateStatusIfCurrent(long id, String status, String expectedStatus) {
        return jdbcTemplate.update(
                "UPDATE product SET status = ? WHERE id = ? AND status = ?",
                status,
                id,
                expectedStatus
        );
    }

    public long countQuoteItems(long productId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM quote_item WHERE product_id = ?",
                Long.class,
                productId
        );
        return count == null ? 0L : count;
    }

    public int deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM product WHERE id = ?", id);
    }

    public int updateImagePath(long id, String imagePath) {
        return jdbcTemplate.update(
                "UPDATE product SET image_path = ? WHERE id = ?",
                imagePath,
                id
        );
    }

    public Optional<Long> findIdByProductCode(String productCode) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM product WHERE product_code = ?",
                (rs, rowNum) -> rs.getLong("id"),
                productCode
        );
        return ids.stream().findFirst();
    }

    public long insert(
            long categoryId,
            String productCode,
            String name,
            BigDecimal unitPrice,
            BigDecimal salePrice,
            String specification,
            String description
    ) {
        String sql = """
                INSERT INTO product (
                    category_id, product_code, name, unit_price, sale_price, specification, description
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, categoryId);
                statement.setString(2, productCode);
                statement.setString(3, name);
                statement.setBigDecimal(4, unitPrice);
                statement.setBigDecimal(5, salePrice);
                setNullableString(statement, 6, specification);
                setNullableString(statement, 7, description);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new ProductCodeExistsException();
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("product id was not generated");
        }
        return key.longValue();
    }

    public int update(
            long id,
            long categoryId,
            String productCode,
            String name,
            BigDecimal unitPrice,
            BigDecimal salePrice,
            String specification,
            String description
    ) {
        String sql = """
                UPDATE product
                SET category_id = ?,
                    product_code = ?,
                    name = ?,
                    unit_price = ?,
                    sale_price = ?,
                    specification = ?,
                    description = ?
                WHERE id = ?
                """;
        try {
            return jdbcTemplate.update(sql, ps -> {
                ps.setLong(1, categoryId);
                ps.setString(2, productCode);
                ps.setString(3, name);
                ps.setBigDecimal(4, unitPrice);
                ps.setBigDecimal(5, salePrice);
                setNullableString(ps, 6, specification);
                setNullableString(ps, 7, description);
                ps.setLong(8, id);
            });
        } catch (DuplicateKeyException exception) {
            throw new ProductCodeExistsException();
        }
    }

    private static int bindAdminFilter(
            PreparedStatement statement,
            String keyword,
            Long categoryId,
            BigDecimal salePrice,
            String status
    ) throws SQLException {
        boolean hasKeyword = keyword != null;
        String pattern = hasKeyword ? toContainsPattern(keyword) : "%";
        statement.setInt(1, hasKeyword ? 1 : 0);
        statement.setString(2, pattern);
        statement.setString(3, pattern);
        statement.setString(4, pattern);
        statement.setString(5, pattern);
        statement.setInt(6, categoryId == null ? 0 : 1);
        if (categoryId == null) {
            statement.setNull(7, Types.BIGINT);
        } else {
            statement.setLong(7, categoryId);
        }
        statement.setInt(8, salePrice == null ? 0 : 1);
        if (salePrice == null) {
            statement.setNull(9, Types.DECIMAL);
        } else {
            statement.setBigDecimal(9, salePrice);
        }
        boolean hasStatus = status != null;
        statement.setInt(10, hasStatus ? 1 : 0);
        if (hasStatus) {
            statement.setString(11, status);
        } else {
            statement.setNull(11, Types.VARCHAR);
        }
        return 12;
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private static final RowMapper<AdminProductSummaryResponse> ADMIN_SUMMARY_MAPPER = (rs, rowNum) ->
            new AdminProductSummaryResponse(
                    rs.getLong("id"),
                    rs.getString("image_path"),
                    rs.getString("name"),
                    rs.getString("product_code"),
                    rs.getBigDecimal("sale_price"),
                    rs.getString("status")
            );

    private static final RowMapper<AdminProductDetailResponse> ADMIN_DETAIL_MAPPER = (rs, rowNum) ->
            new AdminProductDetailResponse(
                    rs.getLong("id"),
                    rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getString("name"),
                    rs.getString("product_code"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("sale_price"),
                    rs.getString("specification"),
                    rs.getString("description"),
                    rs.getString("image_path"),
                    rs.getString("status"),
                    List.of()
            );
}
