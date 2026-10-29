package net.likelion.bebc25.quoteon.quote;

import net.likelion.bebc25.quoteon.quote.dto.QuoteItemResponse;
import net.likelion.bebc25.quoteon.quote.dto.QuoteResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class QuoteRepository {

    static final String DRAFT = "DRAFT";

    private final JdbcTemplate jdbcTemplate;

    public QuoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertQuote(
            String quoteNumber,
            long memberId,
            long customerId,
            BigDecimal supplyAmount,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            LocalDate quoteDate
    ) {
        String sql = """
                INSERT INTO quote (
                    quote_number, member_id, customer_id,
                    supply_amount, vat_amount, total_amount, status, quote_date
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, quoteNumber);
            statement.setLong(2, memberId);
            statement.setLong(3, customerId);
            statement.setBigDecimal(4, supplyAmount);
            statement.setBigDecimal(5, vatAmount);
            statement.setBigDecimal(6, totalAmount);
            statement.setString(7, DRAFT);
            statement.setDate(8, Date.valueOf(quoteDate));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("quote id was not generated");
        }
        return key.longValue();
    }

    public void insertItems(long quoteId, List<QuoteItemWrite> items) {
        String sql = """
                INSERT INTO quote_item (
                    quote_id, product_id, product_name, product_code, sale_price, quantity, amount
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        for (QuoteItemWrite item : items) {
            jdbcTemplate.update(
                    sql,
                    quoteId,
                    item.productId(),
                    item.productName(),
                    item.productCode(),
                    item.salePrice(),
                    item.quantity(),
                    item.amount()
            );
        }
    }

    public Optional<QuoteResponse> findById(long id) {
        String sql = """
                SELECT id, quote_number, customer_id, supply_amount, vat_amount, total_amount, status, quote_date
                FROM quote
                WHERE id = ?
                """;
        List<QuoteHeader> headers = jdbcTemplate.query(sql, ps -> ps.setLong(1, id), (rs, rowNum) -> new QuoteHeader(
                rs.getLong("id"),
                rs.getString("quote_number"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("supply_amount"),
                rs.getBigDecimal("vat_amount"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                rs.getObject("quote_date", LocalDate.class)
        ));
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        QuoteHeader header = headers.get(0);
        return Optional.of(new QuoteResponse(
                header.id(),
                header.quoteNumber(),
                header.customerId(),
                header.supplyAmount(),
                header.vatAmount(),
                header.totalAmount(),
                header.status(),
                header.quoteDate(),
                findItems(id)
        ));
    }

    private List<QuoteItemResponse> findItems(long quoteId) {
        String sql = """
                SELECT id, product_id, product_name, product_code, sale_price, quantity, amount
                FROM quote_item
                WHERE quote_id = ?
                ORDER BY id
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, quoteId), (rs, rowNum) -> new QuoteItemResponse(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("product_code"),
                rs.getBigDecimal("sale_price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("amount")
        ));
    }

    private record QuoteHeader(
            long id,
            String quoteNumber,
            long customerId,
            BigDecimal supplyAmount,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            String status,
            LocalDate quoteDate
    ) {
    }
}
