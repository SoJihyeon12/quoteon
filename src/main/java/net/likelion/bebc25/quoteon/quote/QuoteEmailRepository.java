package net.likelion.bebc25.quoteon.quote;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class QuoteEmailRepository {

    static final String SUCCESS = "SUCCESS";
    static final String FAILED = "FAILED";

    private final JdbcTemplate jdbcTemplate;

    public QuoteEmailRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<QuoteEmailHistory> findByQuoteId(long quoteId) {
        return jdbcTemplate.query(
                """
                SELECT id, recipient_email, subject, status, failure_reason, sent_at, created_at
                FROM quote_email_history
                WHERE quote_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                (rs, rowNum) -> mapHistory(rs),
                quoteId
        );
    }

    public Optional<QuoteEmailHistory> findByIdAndQuoteId(long historyId, long quoteId) {
        List<QuoteEmailHistory> histories = jdbcTemplate.query(
                """
                SELECT id, recipient_email, subject, status, failure_reason, sent_at, created_at
                FROM quote_email_history
                WHERE id = ? AND quote_id = ?
                """,
                (rs, rowNum) -> mapHistory(rs),
                historyId,
                quoteId
        );
        if (histories.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(histories.get(0));
    }

    public int markSent(long quoteId, long memberId) {
        return jdbcTemplate.update(
                "UPDATE quote SET status = 'SENT' WHERE id = ? AND member_id = ?",
                quoteId,
                memberId
        );
    }

    public void insert(
            long quoteId,
            String recipient,
            String subject,
            String status,
            String failureReason,
            LocalDateTime sentAt,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO quote_email_history (
                    quote_id, recipient_email, subject, status, failure_reason, sent_at, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                quoteId,
                recipient,
                subject,
                status,
                failureReason,
                timestamp(sentAt),
                timestamp(createdAt)
        );
    }

    private static QuoteEmailHistory mapHistory(ResultSet rs) throws SQLException {
        return new QuoteEmailHistory(
                rs.getLong("id"),
                rs.getString("recipient_email"),
                rs.getString("subject"),
                rs.getString("status"),
                rs.getString("failure_reason"),
                localDateTime(rs.getTimestamp("sent_at")),
                localDateTime(rs.getTimestamp("created_at"))
        );
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
