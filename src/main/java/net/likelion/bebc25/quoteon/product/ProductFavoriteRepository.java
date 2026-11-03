package net.likelion.bebc25.quoteon.product;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductFavoriteRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductFavoriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean exists(long memberId, long productId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM product_favorite
                        WHERE member_id = ?
                          AND product_id = ?
                        """,
                Long.class,
                memberId,
                productId
        );
        return count != null && count > 0;
    }

    public void insert(long memberId, long productId) {
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO product_favorite (member_id, product_id)
                            VALUES (?, ?)
                            """,
                    memberId,
                    productId
            );
        } catch (DuplicateKeyException exception) {
            throw new FavoriteAlreadyExistsException();
        }
    }

    public int delete(long memberId, long productId) {
        return jdbcTemplate.update(
                """
                        DELETE FROM product_favorite
                        WHERE member_id = ?
                          AND product_id = ?
                        """,
                memberId,
                productId
        );
    }
}
