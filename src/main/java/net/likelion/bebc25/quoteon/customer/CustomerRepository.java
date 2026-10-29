package net.likelion.bebc25.quoteon.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CustomerRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CustomerRef> findById(long id) {
        String sql = """
                SELECT id, member_id
                FROM customer
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, id), (rs, rowNum) -> new CustomerRef(
                rs.getLong("id"),
                rs.getLong("member_id")
        )).stream().findFirst();
    }
}
