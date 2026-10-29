package net.likelion.bebc25.quoteon.member;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MemberRepository {

    private static final RowMapper<PendingMember> PENDING_ROW_MAPPER = (rs, rowNum) -> new PendingMember(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("company_name"),
            rs.getString("role"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private static final RowMapper<Member> ROW_MAPPER = (rs, rowNum) -> new Member(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password"),
            rs.getString("name"),
            rs.getString("phone"),
            rs.getString("company_name"),
            rs.getString("role"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbcTemplate;

    public MemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Member> findByEmail(String email) {
        String sql = """
                SELECT id, email, password, name, phone, company_name, role, status
                FROM member
                WHERE email = ?
                """;
        return jdbcTemplate.query(sql, ps -> ps.setString(1, email), ROW_MAPPER).stream().findFirst();
    }

    public Optional<Member> findById(long id) {
        String sql = """
                SELECT id, email, password, name, phone, company_name, role, status
                FROM member
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, id), ROW_MAPPER).stream().findFirst();
    }

    public long insertPending(
            String email,
            String passwordHash,
            String name,
            String phone,
            String companyName,
            String role
    ) {
        String sql = """
                INSERT INTO member (email, password, name, phone, company_name, role, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, email);
                statement.setString(2, passwordHash);
                statement.setString(3, name);
                setNullableString(statement, 4, phone);
                setNullableString(statement, 5, companyName);
                statement.setString(6, role);
                statement.setString(7, MemberStatus.PENDING);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyExistsException();
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("member id was not generated");
        }
        return key.longValue();
    }

    public List<PendingMember> findPending(String keyword, int limit, long offset) {
        String sql = """
                SELECT id, name, email, phone, company_name, role, created_at
                FROM member
                WHERE status = ?
                  AND (
                        ? = 0
                        OR name LIKE ? ESCAPE '\\\\'
                        OR email LIKE ? ESCAPE '\\\\'
                        OR company_name LIKE ? ESCAPE '\\\\'
                      )
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, ps -> {
            int next = bindPendingMatch(ps, keyword);
            ps.setInt(next, limit);
            ps.setLong(next + 1, offset);
        }, PENDING_ROW_MAPPER);
    }

    public long countPending(String keyword) {
        String sql = """
                SELECT COUNT(*)
                FROM member
                WHERE status = ?
                  AND (
                        ? = 0
                        OR name LIKE ? ESCAPE '\\\\'
                        OR email LIKE ? ESCAPE '\\\\'
                        OR company_name LIKE ? ESCAPE '\\\\'
                      )
                """;
        List<Long> counts = jdbcTemplate.query(
                sql,
                ps -> bindPendingMatch(ps, keyword),
                (rs, rowNum) -> rs.getLong(1)
        );
        return counts.isEmpty() ? 0L : counts.get(0);
    }

    public int updateStatusIfPending(long id, String status) {
        String sql = """
                UPDATE member
                SET status = ?
                WHERE id = ?
                  AND status = ?
                """;
        return jdbcTemplate.update(sql, status, id, MemberStatus.PENDING);
    }

    private static int bindPendingMatch(PreparedStatement statement, String keyword) throws SQLException {
        boolean hasKeyword = keyword != null;
        String pattern = hasKeyword ? toContainsPattern(keyword) : "%";
        statement.setString(1, MemberStatus.PENDING);
        statement.setInt(2, hasKeyword ? 1 : 0);
        statement.setString(3, pattern);
        statement.setString(4, pattern);
        statement.setString(5, pattern);
        return 6;
    }

    private static String toContainsPattern(String keyword) {
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }
}
