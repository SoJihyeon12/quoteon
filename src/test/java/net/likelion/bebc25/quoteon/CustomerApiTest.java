package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsOnlyOwnActiveCustomersInIdOrder() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        long olderId = insertCustomer(seller.id(), "나중에만든이름", "01011110000", "COMPANY", "ACTIVE");
        long newerId = insertCustomer(seller.id(), "먼저보일이름", "01022220000", "BUSINESS", "ACTIVE");
        insertCustomer(seller.id(), "비활성고객", "01033330000", "COMPANY", "INACTIVE");
        long foreignId = insertCustomer(other.id(), "다른회원고객", "01044440000", "COMPANY", "ACTIVE");

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("memberId", String.valueOf(other.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(olderId)))
                .andExpect(jsonPath("$.items[0].name").value("나중에만든이름"))
                .andExpect(jsonPath("$.items[0].phone").value("01011110000"))
                .andExpect(jsonPath("$.items[0].customerType").value("COMPANY"))
                .andExpect(jsonPath("$.items[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].address").doesNotExist())
                .andExpect(jsonPath("$.items[0].memo").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(jsonId(newerId)))
                .andExpect(jsonPath("$.items[1].name").value("먼저보일이름"))
                .andExpect(jsonPath("$.items[1].customerType").value("BUSINESS"));

        MvcResult otherList = mockMvc.perform(get("/api/customers").session(other.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(foreignId)))
                .andReturn();
        String body = otherList.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalseContains(body, olderId);
    }

    @Test
    void searchesNameOrPhoneByContains() throws Exception {
        SalesMember seller = loginActiveSales();
        long hongId = insertCustomer(seller.id(), "홍길동", "01011112222", "COMPANY", "ACTIVE");
        long kimId = insertCustomer(seller.id(), "김철수", "01099998888", "BUSINESS", "ACTIVE");
        insertCustomer(seller.id(), "홍길동", "01055550000", "COMPANY", "INACTIVE");
        SalesMember other = loginActiveSales();
        insertCustomer(other.id(), "홍길동", "01011112222", "COMPANY", "ACTIVE");

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("keyword", " 길동 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(hongId)))
                .andExpect(jsonPath("$.items[0].name").value("홍길동"));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("keyword", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(kimId)))
                .andExpect(jsonPath("$.items[0].phone").value("01099998888"));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("keyword", "없는고객"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void returnsEmptyPageWhenMemberHasNoCustomers() throws Exception {
        SalesMember seller = loginActiveSales();

        mockMvc.perform(get("/api/customers").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void paginatesAndRejectsInvalidPage() throws Exception {
        SalesMember seller = loginActiveSales();
        long firstId = insertCustomer(seller.id(), "고객1", "01010000001", "COMPANY", "ACTIVE");
        long secondId = insertCustomer(seller.id(), "고객2", "01010000002", "COMPANY", "ACTIVE");
        long thirdId = insertCustomer(seller.id(), "고객3", "01010000003", "COMPANY", "ACTIVE");

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(firstId)))
                .andExpect(jsonPath("$.items[1].id").value(jsonId(secondId)));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(thirdId)));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));
        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
    }

    @Test
    void rejectsUnauthenticatedCustomerList() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private SalesMember loginActiveSales() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"SALES","name":"고객회원","email":"%s","password":"password1234","phone":"01012345678","companyName":"테스트회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING' AND email = ?",
                id.longValue(),
                email
        );
        if (updated != 1) {
            throw new IllegalStateException("test member was not activated");
        }
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new SalesMember(id.longValue(), (MockHttpSession) login.getRequest().getSession(false));
    }

    private long insertCustomer(long memberId, String name, String phone, String customerType, String status) {
        String sql = """
                INSERT INTO customer (member_id, name, customer_type, phone, status)
                VALUES (?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, memberId);
            statement.setString(2, name);
            statement.setString(3, customerType);
            statement.setString(4, phone);
            statement.setString(5, status);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("customer id was not generated");
        }
        return key.longValue();
    }

    private static void assertFalseContains(String body, long id) {
        if (body.contains("\"id\":" + id) || body.contains("\"id\": " + id)) {
            throw new AssertionError("other member list included customer " + id);
        }
    }

    private static int jsonId(long id) {
        return Math.toIntExact(id);
    }

    private record SalesMember(long id, MockHttpSession session) {
    }
}
