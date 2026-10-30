package net.likelion.bebc25.quoteon.category;

import net.likelion.bebc25.quoteon.category.dto.CategoryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Repository
public class CategoryRepository {

    private static final RowMapper<CategoryResponse> ROW_MAPPER = CategoryRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public CategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CategoryResponse> findAll() {
        String sql = """
                SELECT id, name, parent_id
                FROM category
                ORDER BY COALESCE(parent_id, id), parent_id IS NOT NULL, id
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public Optional<CategoryResponse> findById(long id) {
        String sql = """
                SELECT id, name, parent_id
                FROM category
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, id), ROW_MAPPER).stream().findFirst();
    }

    public Optional<Long> findIdByName(String name) {
        String sql = """
                SELECT id
                FROM category
                WHERE name = ?
                """;
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), name);
        return ids.stream().findFirst();
    }

    public Long findParentId(long id) {
        String sql = """
                SELECT parent_id
                FROM category
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            long parentId = rs.getLong("parent_id");
            return rs.wasNull() ? null : parentId;
        }, id);
    }

    public long insert(String name, Long parentId) {
        String sql = """
                INSERT INTO category (name, parent_id)
                VALUES (?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            setNullableLong(statement, 2, parentId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("category id was not generated");
        }
        return key.longValue();
    }

    public int update(long id, String name, Long parentId) {
        String sql = """
                UPDATE category
                SET name = ?, parent_id = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, ps -> {
            ps.setString(1, name);
            setNullableLong(ps, 2, parentId);
            ps.setLong(3, id);
        });
    }

    public int delete(long id) {
        return jdbcTemplate.update("DELETE FROM category WHERE id = ?", id);
    }

    public long countChildren(long id) {
        return count("SELECT COUNT(*) FROM category WHERE parent_id = ?", id);
    }

    public long countProducts(long id) {
        return count("SELECT COUNT(*) FROM product WHERE category_id = ?", id);
    }

    private long count(String sql, long id) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
        return count == null ? 0L : count;
    }

    private static CategoryResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CategoryResponse(
                rs.getLong("id"),
                rs.getString("name"),
                parentId(rs)
        );
    }

    private static Long parentId(ResultSet rs) throws SQLException {
        long parentId = rs.getLong("parent_id");
        return rs.wasNull() ? null : parentId;
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }
}
