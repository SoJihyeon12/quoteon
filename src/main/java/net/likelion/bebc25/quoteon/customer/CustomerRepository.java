package net.likelion.bebc25.quoteon.customer;

import net.likelion.bebc25.quoteon.customer.dto.CustomerResponse;
import net.likelion.bebc25.quoteon.customer.dto.CustomerSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class CustomerRepository {

    public static final String ACTIVE_STATUS = "ACTIVE";
    public static final String INACTIVE_STATUS = "INACTIVE";

    private static final String SELECTABLE_FILTER = """
            member_id = ?
              AND status = ?
              AND (
                    ? = 0
                    OR name LIKE ? ESCAPE '\\\\'
                    OR phone LIKE ? ESCAPE '\\\\'
                  )
              AND (
                    ? = 0
                    OR customer_type = ?
                  )
            """;

    private final JdbcTemplate jdbcTemplate;

    public CustomerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(
            long memberId,
            String name,
            String customerType,
            String phone,
            String email,
            String address,
            String memo
    ) {
        String sql = """
                INSERT INTO customer (member_id, name, customer_type, phone, email, address, memo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, memberId);
            statement.setString(2, name);
            statement.setString(3, customerType);
            statement.setString(4, phone);
            statement.setString(5, email);
            statement.setString(6, address);
            statement.setString(7, memo);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("customer id was not generated");
        }
        return key.longValue();
    }

    public int update(
            long id,
            long memberId,
            String name,
            String customerType,
            String phone,
            String email,
            String address,
            String memo
    ) {
        String sql = """
                UPDATE customer
                SET name = ?, customer_type = ?, phone = ?, email = ?, address = ?, memo = ?
                WHERE id = ? AND member_id = ?
                """;
        return jdbcTemplate.update(
                sql,
                name,
                customerType,
                phone,
                email,
                address,
                memo,
                id,
                memberId
        );
    }

    public Optional<CustomerDetail> findDetailById(long id) {
        String sql = """
                SELECT id, member_id, name, customer_type, phone, email, address, memo
                FROM customer
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, id), (rs, rowNum) -> new CustomerDetail(
                rs.getLong("member_id"),
                new CustomerResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("customer_type"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getString("address"),
                        rs.getString("memo")
                )
        )).stream().findFirst();
    }

    public int deactivate(long id, long memberId) {
        String sql = """
                UPDATE customer
                SET status = ?
                WHERE id = ? AND member_id = ? AND status = ?
                """;
        return jdbcTemplate.update(sql, INACTIVE_STATUS, id, memberId, ACTIVE_STATUS);
    }

    public Optional<CustomerRef> findById(long id) {
        String sql = """
                SELECT id, member_id, status
                FROM customer
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, id), (rs, rowNum) -> new CustomerRef(
                rs.getLong("id"),
                rs.getLong("member_id"),
                rs.getString("status")
        )).stream().findFirst();
    }

    public long countSelectable(long memberId, String keyword, String customerType) {
        String sql = "SELECT COUNT(*) FROM customer WHERE " + SELECTABLE_FILTER;
        Long count = jdbcTemplate.query(sql, ps -> {
            bindSelectable(ps, memberId, keyword, customerType);
        }, rs -> {
            if (!rs.next()) {
                return 0L;
            }
            return rs.getLong(1);
        });
        return count == null ? 0 : count;
    }

    public List<CustomerSummaryResponse> findSelectable(
            long memberId,
            String keyword,
            String customerType,
            int limit,
            long offset
    ) {
        String sql = """
                SELECT id, name, phone, customer_type
                FROM customer
                WHERE
                """ + SELECTABLE_FILTER + """
                ORDER BY id
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, ps -> {
            int next = bindSelectable(ps, memberId, keyword, customerType);
            ps.setInt(next, limit);
            ps.setLong(next + 1, offset);
        }, (rs, rowNum) -> new CustomerSummaryResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("customer_type")
        ));
    }

    private int bindSelectable(
            PreparedStatement statement,
            long memberId,
            String keyword,
            String customerType
    ) throws SQLException {
        boolean hasKeyword = keyword != null;
        boolean hasType = customerType != null;
        String pattern = hasKeyword ? toContainsPattern(keyword) : "%";
        statement.setLong(1, memberId);
        statement.setString(2, ACTIVE_STATUS);
        statement.setInt(3, hasKeyword ? 1 : 0);
        statement.setString(4, pattern);
        statement.setString(5, pattern);
        statement.setInt(6, hasType ? 1 : 0);
        statement.setString(7, hasType ? customerType : "");
        return 8;
    }

    private static String toContainsPattern(String keyword) {
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
