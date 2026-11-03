package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteCustomerResponse;
import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteDetailResponse;
import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteItemResponse;
import net.likelion.bebc25.quoteon.quote.dto.AdminQuoteSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class AdminQuoteRepository {

    private static final String FILTER = """
            WHERE (? = 0 OR q.quote_date >= ?)
              AND (? = 0 OR q.quote_date <= ?)
              AND (? = 0 OR m.name LIKE ? ESCAPE '\\\\')
              AND (? = 0 OR c.name LIKE ? ESCAPE '\\\\')
              AND (? = 0 OR q.status = ?)
              AND (
                    ? = 0
                    OR EXISTS (
                        SELECT 1
                        FROM quote_item qi
                        WHERE qi.quote_id = q.id
                          AND (
                                qi.product_name LIKE ? ESCAPE '\\\\'
                                OR qi.product_code LIKE ? ESCAPE '\\\\'
                          )
                    )
              )
            """;

    private final JdbcTemplate jdbcTemplate;

    public AdminQuoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(AdminQuoteFilter filter) {
        String sql = """
                SELECT COUNT(*)
                FROM quote q
                INNER JOIN member m ON m.id = q.member_id
                INNER JOIN customer c ON c.id = q.customer_id
                """ + FILTER;
        Long count = jdbcTemplate.query(sql, ps -> bindFilter(ps, filter), rs -> {
            rs.next();
            return rs.getLong(1);
        });
        return count == null ? 0 : count;
    }

    public List<AdminQuoteSummaryResponse> find(AdminQuoteFilter filter, int limit, long offset) {
        String sql = """
                SELECT q.id, q.quote_date, m.name AS author_name, c.name AS customer_name,
                       q.total_amount, q.status
                FROM quote q
                INNER JOIN member m ON m.id = q.member_id
                INNER JOIN customer c ON c.id = q.customer_id
                """ + FILTER + """
                ORDER BY q.quote_date DESC, q.id DESC
                LIMIT ? OFFSET ?
                """;
        return jdbcTemplate.query(sql, ps -> {
            int next = bindFilter(ps, filter);
            ps.setInt(next, limit);
            ps.setLong(next + 1, offset);
        }, (rs, rowNum) -> new AdminQuoteSummaryResponse(
                rs.getLong("id"),
                rs.getObject("quote_date", LocalDate.class),
                rs.getString("author_name"),
                rs.getString("customer_name"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status")
        ));
    }

    public int deleteById(long quoteId) {
        return jdbcTemplate.update("DELETE FROM quote WHERE id = ?", quoteId);
    }

    public Optional<AdminQuoteDetailResponse> findDetail(long quoteId) {
        String sql = """
                SELECT q.id, q.quote_number, q.quote_date, m.name AS author_name, q.status, q.memo,
                       q.supply_amount, q.vat_amount, q.total_amount,
                       c.id AS customer_id, c.name AS customer_name, c.customer_type,
                       c.phone, c.email, c.address
                FROM quote q
                INNER JOIN member m ON m.id = q.member_id
                INNER JOIN customer c ON c.id = q.customer_id
                WHERE q.id = ?
                """;
        List<AdminQuoteDetailResponse> headers = jdbcTemplate.query(
                sql,
                ps -> ps.setLong(1, quoteId),
                (rs, rowNum) -> new AdminQuoteDetailResponse(
                        rs.getLong("id"),
                        rs.getString("quote_number"),
                        rs.getObject("quote_date", LocalDate.class),
                        rs.getString("author_name"),
                        rs.getString("status"),
                        rs.getString("memo"),
                        rs.getBigDecimal("supply_amount"),
                        rs.getBigDecimal("vat_amount"),
                        rs.getBigDecimal("total_amount"),
                        new AdminQuoteCustomerResponse(
                                rs.getLong("customer_id"),
                                rs.getString("customer_name"),
                                rs.getString("customer_type"),
                                rs.getString("phone"),
                                rs.getString("email"),
                                rs.getString("address")
                        ),
                        List.of()
                )
        );
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        AdminQuoteDetailResponse header = headers.get(0);
        return Optional.of(new AdminQuoteDetailResponse(
                header.id(),
                header.quoteNumber(),
                header.quoteDate(),
                header.authorName(),
                header.status(),
                header.memo(),
                header.supplyAmount(),
                header.vatAmount(),
                header.totalAmount(),
                header.customer(),
                findItems(header.id())
        ));
    }

    private List<AdminQuoteItemResponse> findItems(long quoteId) {
        String sql = """
                SELECT id, product_name, product_code, specification, specification_source,
                       sale_price, quantity, amount
                FROM quote_item
                WHERE quote_id = ?
                ORDER BY id
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, quoteId), (rs, rowNum) -> new AdminQuoteItemResponse(
                rs.getLong("id"),
                rs.getString("product_name"),
                rs.getString("product_code"),
                rs.getString("specification"),
                rs.getString("specification_source"),
                rs.getBigDecimal("sale_price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("amount")
        ));
    }

    private static int bindFilter(PreparedStatement statement, AdminQuoteFilter filter) throws SQLException {
        int index = 1;
        index = bindDate(statement, index, filter.quoteDateFrom());
        index = bindDate(statement, index, filter.quoteDateTo());
        index = bindLike(statement, index, filter.authorName());
        index = bindLike(statement, index, filter.customerName());
        index = bindExact(statement, index, filter.status());
        boolean hasKeyword = filter.productKeyword() != null;
        statement.setInt(index++, hasKeyword ? 1 : 0);
        String pattern = hasKeyword ? containsPattern(filter.productKeyword()) : "%";
        statement.setString(index++, pattern);
        statement.setString(index++, pattern);
        return index;
    }

    private static int bindDate(PreparedStatement statement, int index, LocalDate date) throws SQLException {
        statement.setInt(index++, date == null ? 0 : 1);
        if (date == null) {
            statement.setNull(index++, Types.DATE);
        } else {
            statement.setDate(index++, Date.valueOf(date));
        }
        return index;
    }

    private static int bindLike(PreparedStatement statement, int index, String value) throws SQLException {
        statement.setInt(index++, value == null ? 0 : 1);
        statement.setString(index++, value == null ? "%" : containsPattern(value));
        return index;
    }

    private static int bindExact(PreparedStatement statement, int index, String value) throws SQLException {
        statement.setInt(index++, value == null ? 0 : 1);
        if (value == null) {
            statement.setNull(index++, Types.VARCHAR);
        } else {
            statement.setString(index++, value);
        }
        return index;
    }

    private static String containsPattern(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
