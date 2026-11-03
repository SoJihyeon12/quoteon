package net.likelion.bebc25.quoteon.admin;

import net.likelion.bebc25.quoteon.admin.dto.MemberQuoteRankResponse;
import net.likelion.bebc25.quoteon.member.MemberRole;
import net.likelion.bebc25.quoteon.member.MemberStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AdminDashboardRepository {

    private static final String DRAFT = "DRAFT";
    private static final String SENT = "SENT";

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countQuotes(LocalDateTime from, LocalDateTime to) {
        String sql = """
                SELECT COUNT(*)
                FROM quote
                WHERE created_at >= ?
                  AND created_at < ?
                  AND status IN (?, ?)
                """;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, timestamp(from), timestamp(to), DRAFT, SENT);
        return count == null ? 0L : count;
    }

    public BigDecimal sumQuoteTotalAmount(LocalDateTime from, LocalDateTime to) {
        String sql = """
                SELECT COALESCE(SUM(total_amount), CAST(0 AS DECIMAL(14, 2)))
                FROM quote
                WHERE created_at >= ?
                  AND created_at < ?
                  AND status IN (?, ?)
                """;
        BigDecimal amount = jdbcTemplate.queryForObject(
                sql,
                BigDecimal.class,
                timestamp(from),
                timestamp(to),
                DRAFT,
                SENT
        );
        return amount == null ? new BigDecimal("0.00") : amount;
    }

    public long countMembersByStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE status = ?",
                Long.class,
                status
        );
        return count == null ? 0L : count;
    }

    public long countActiveSalesMembers() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE status = ? AND role = ?",
                Long.class,
                MemberStatus.ACTIVE,
                MemberRole.USER
        );
        return count == null ? 0L : count;
    }

    public List<ProductQuoteCount> countQuotesByProductCode(LocalDateTime from, LocalDateTime to) {
        String sql = """
                SELECT qi.product_code, COUNT(DISTINCT qi.quote_id) AS quote_count
                FROM quote_item qi
                INNER JOIN quote q ON q.id = qi.quote_id
                WHERE q.created_at >= ?
                  AND q.created_at < ?
                  AND q.status IN (?, ?)
                GROUP BY qi.product_code
                ORDER BY quote_count DESC, qi.product_code ASC
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProductQuoteCount(rs.getString("product_code"), rs.getLong("quote_count")),
                timestamp(from),
                timestamp(to),
                DRAFT,
                SENT
        );
    }

    public List<MemberQuoteRankResponse> findMemberQuoteRanks(LocalDateTime from, LocalDateTime to) {
        String sql = """
                SELECT m.id, m.name, COUNT(q.id) AS quote_count
                FROM member m
                INNER JOIN quote q ON q.member_id = m.id
                WHERE m.role = ?
                  AND q.created_at >= ?
                  AND q.created_at < ?
                  AND q.status IN (?, ?)
                GROUP BY m.id, m.name
                ORDER BY quote_count DESC, m.id ASC
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new MemberQuoteRankResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("quote_count")
                ),
                MemberRole.USER,
                timestamp(from),
                timestamp(to),
                DRAFT,
                SENT
        );
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    public record ProductQuoteCount(String productCode, long quoteCount) {
    }
}
