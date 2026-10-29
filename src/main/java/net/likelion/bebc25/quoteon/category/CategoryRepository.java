package net.likelion.bebc25.quoteon.category;

import net.likelion.bebc25.quoteon.category.dto.CategoryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

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
}
