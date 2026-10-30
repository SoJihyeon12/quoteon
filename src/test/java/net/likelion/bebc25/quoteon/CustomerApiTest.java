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

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        long olderId = insertCustomer(seller.id(), "나중에만든이름", "01011110000", "기업", "ACTIVE");
        long newerId = insertCustomer(seller.id(), "먼저보일이름", "01022220000", "개인", "ACTIVE");
        insertCustomer(seller.id(), "비활성고객", "01033330000", "기업", "INACTIVE");
        long foreignId = insertCustomer(other.id(), "다른회원고객", "01044440000", "기업", "ACTIVE");

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
                .andExpect(jsonPath("$.items[0].customerType").value("기업"))
                .andExpect(jsonPath("$.items[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.items[0].address").doesNotExist())
                .andExpect(jsonPath("$.items[0].memo").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(jsonId(newerId)))
                .andExpect(jsonPath("$.items[1].name").value("먼저보일이름"))
                .andExpect(jsonPath("$.items[1].customerType").value("개인"));

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
        long hongId = insertCustomer(seller.id(), "홍길동", "01011112222", "기업", "ACTIVE");
        long kimId = insertCustomer(seller.id(), "김철수", "01099998888", "개인", "ACTIVE");
        insertCustomer(seller.id(), "홍길동", "01055550000", "기업", "INACTIVE");
        SalesMember other = loginActiveSales();
        insertCustomer(other.id(), "홍길동", "01011112222", "기업", "ACTIVE");

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
        long firstId = insertCustomer(seller.id(), "고객1", "01010000001", "기업", "ACTIVE");
        long secondId = insertCustomer(seller.id(), "고객2", "01010000002", "기업", "ACTIVE");
        long thirdId = insertCustomer(seller.id(), "고객3", "01010000003", "기업", "ACTIVE");

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
    void createsCustomerForTheAuthenticatedMember() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();

        MvcResult result = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":999999999,"status":"INACTIVE","name":"홍길동","customerType":"기업","phone":"01011112222","email":"hong@example.com","address":"서울시","memo":"첫 상담"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.customerType").value("기업"))
                .andExpect(jsonPath("$.phone").value("01011112222"))
                .andExpect(jsonPath("$.email").value("hong@example.com"))
                .andExpect(jsonPath("$.address").value("서울시"))
                .andExpect(jsonPath("$.memo").value("첫 상담"))
                .andExpect(jsonPath("$.memberId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        StoredCustomer stored = storedCustomer(id.longValue());
        assertEquals(seller.id(), stored.memberId());
        assertEquals("ACTIVE", stored.status());

        mockMvc.perform(get("/api/customers").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(id.longValue())))
                .andExpect(jsonPath("$.items[0].name").value("홍길동"))
                .andExpect(jsonPath("$.items[0].customerType").value("기업"));

        mockMvc.perform(get("/api/customers").session(other.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void acceptsIndividualCustomerWithoutOptionalFields() throws Exception {
        SalesMember seller = loginActiveSales();

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"김철수","customerType":"개인"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.customerType").value("개인"))
                .andExpect(jsonPath("$.phone").value(nullValue()))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.address").value(nullValue()))
                .andExpect(jsonPath("$.memo").value(nullValue()));
    }

    @Test
    void rejectsInvalidEmailAndValuesLongerThanColumns() throws Exception {
        SalesMember seller = loginActiveSales();

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","customerType":"기업","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("이메일 형식을 확인해 주세요."));
        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","customerType":"기업"}
                                """.formatted("가".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("고객명은 100자 이하로 입력해 주세요."));
        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","customerType":"%s"}
                                """.formatted("T".repeat(31))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("고객 유형은 개인 또는 기업만 선택할 수 있습니다."));
        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","customerType":"기업","phone":"%s"}
                                """.formatted("1".repeat(21))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("연락처는 20자 이하로 입력해 주세요."));
        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","customerType":"기업","email":"a@%s.co"}
                                """.formatted("b".repeat(252))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일은 255자 이하로 입력해 주세요."));
        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"홍길동","customerType":"기업","address":"%s"}
                                """.formatted("주".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("주소는 500자 이하로 입력해 주세요."));
        assertEquals(0, customerCount(seller.id()));
    }

    @Test
    void rejectsMissingSessionAndMissingCsrfWhenCreatingCustomer() throws Exception {
        SalesMember seller = loginActiveSales();
        String body = """
                {"name":"홍길동","customerType":"기업"}
                """;

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/customers")
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertEquals(0, customerCount(seller.id()));
    }

    @Test
    void readsOwnCustomerDetail() throws Exception {
        SalesMember seller = loginActiveSales();
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"상세고객","customerType":"개인","phone":"01033334444","email":"detail@example.com","address":"부산시","memo":"상세 메모"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        mockMvc.perform(get("/api/customers/{id}", id.longValue()).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(id.longValue())))
                .andExpect(jsonPath("$.name").value("상세고객"))
                .andExpect(jsonPath("$.customerType").value("개인"))
                .andExpect(jsonPath("$.phone").value("01033334444"))
                .andExpect(jsonPath("$.email").value("detail@example.com"))
                .andExpect(jsonPath("$.address").value("부산시"))
                .andExpect(jsonPath("$.memo").value("상세 메모"))
                .andExpect(jsonPath("$.memberId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void rejectsAnotherMembersCustomerDetailAndMissingCustomer() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"비밀고객","customerType":"기업","phone":"01077778888","email":"secret@example.com","address":"대구시","memo":"비공개"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        mockMvc.perform(get("/api/customers/{id}", id.longValue()).session(other.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ACCESS_DENIED"))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.customerType").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.memo").doesNotExist());

        mockMvc.perform(get("/api/customers/{id}", 999999999L).session(seller.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("고객을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    void updatesOwnCustomerAndShowsChangesInDetailAndList() throws Exception {
        SalesMember seller = loginActiveSales();
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"수정전","customerType":"기업","phone":"01011112222","email":"before@example.com","address":"서울시","memo":"수정 전"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"수정후","customerType":"개인","phone":"01099990000","email":"after@example.com","address":"인천시","memo":"수정 후"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(id.longValue())))
                .andExpect(jsonPath("$.name").value("수정후"))
                .andExpect(jsonPath("$.customerType").value("개인"))
                .andExpect(jsonPath("$.phone").value("01099990000"))
                .andExpect(jsonPath("$.email").value("after@example.com"))
                .andExpect(jsonPath("$.address").value("인천시"))
                .andExpect(jsonPath("$.memo").value("수정 후"))
                .andExpect(jsonPath("$.memberId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());

        mockMvc.perform(get("/api/customers/{id}", id.longValue()).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정후"))
                .andExpect(jsonPath("$.customerType").value("개인"))
                .andExpect(jsonPath("$.phone").value("01099990000"))
                .andExpect(jsonPath("$.email").value("after@example.com"))
                .andExpect(jsonPath("$.address").value("인천시"))
                .andExpect(jsonPath("$.memo").value("수정 후"));

        mockMvc.perform(get("/api/customers").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(id.longValue())))
                .andExpect(jsonPath("$.items[0].name").value("수정후"))
                .andExpect(jsonPath("$.items[0].phone").value("01099990000"))
                .andExpect(jsonPath("$.items[0].customerType").value("개인"));
    }

    @Test
    void rejectsAnotherMembersCustomerUpdateAndMissingCustomer() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"비밀고객","customerType":"기업","phone":"01077778888","email":"secret@example.com","address":"대구시","memo":"비공개"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(other.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"탈취","customerType":"개인","phone":"01000000000","email":"stolen@example.com","address":"변경","memo":"변경"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ACCESS_DENIED"))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.customerType").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.memo").doesNotExist());

        mockMvc.perform(get("/api/customers/{id}", id.longValue()).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("비밀고객"))
                .andExpect(jsonPath("$.customerType").value("기업"))
                .andExpect(jsonPath("$.phone").value("01077778888"))
                .andExpect(jsonPath("$.email").value("secret@example.com"))
                .andExpect(jsonPath("$.address").value("대구시"))
                .andExpect(jsonPath("$.memo").value("비공개"));

        mockMvc.perform(put("/api/customers/{id}", 999999999L)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"없는고객","customerType":"기업"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("고객을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    void rejectsInvalidEmailAndValuesLongerThanColumnsOnUpdate() throws Exception {
        SalesMember seller = loginActiveSales();
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"유지고객","customerType":"기업","phone":"01011112222","email":"keep@example.com","address":"서울시","memo":"유지"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"유지고객","customerType":"기업","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("이메일 형식을 확인해 주세요."));
        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","customerType":"기업"}
                                """.formatted("가".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("고객명은 100자 이하로 입력해 주세요."));
        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"유지고객","customerType":"%s"}
                                """.formatted("T".repeat(31))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("고객 유형은 개인 또는 기업만 선택할 수 있습니다."));
        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"유지고객","customerType":"기업","phone":"%s"}
                                """.formatted("1".repeat(21))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("연락처는 20자 이하로 입력해 주세요."));
        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"유지고객","customerType":"기업","email":"a@%s.co"}
                                """.formatted("b".repeat(252))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일은 255자 이하로 입력해 주세요."));
        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"유지고객","customerType":"기업","address":"%s"}
                                """.formatted("주".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("주소는 500자 이하로 입력해 주세요."));

        mockMvc.perform(get("/api/customers/{id}", id.longValue()).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("유지고객"))
                .andExpect(jsonPath("$.email").value("keep@example.com"))
                .andExpect(jsonPath("$.phone").value("01011112222"))
                .andExpect(jsonPath("$.address").value("서울시"))
                .andExpect(jsonPath("$.memo").value("유지"));
    }

    @Test
    void ignoresMemberIdAndStatusWhenUpdatingCustomer() throws Exception {
        SalesMember seller = loginActiveSales();
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"소유확인","customerType":"기업","phone":"01011112222"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        mockMvc.perform(put("/api/customers/{id}", id.longValue())
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":999999999,"status":"INACTIVE","name":"이름만변경","customerType":"개인","phone":"01022223333","email":"owned@example.com","address":"광주시","memo":"소유 유지"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이름만변경"))
                .andExpect(jsonPath("$.memberId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());

        StoredCustomer stored = storedCustomer(id.longValue());
        assertEquals(seller.id(), stored.memberId());
        assertEquals("ACTIVE", stored.status());
    }

    @Test
    void rejectsUnauthenticatedAndMissingCsrfWhenUpdatingCustomer() throws Exception {
        SalesMember seller = loginActiveSales();
        long id = insertCustomer(seller.id(), "인증고객", "01011112222", "기업", "ACTIVE");
        String body = """
                {"name":"변경","customerType":"개인"}
                """;

        mockMvc.perform(put("/api/customers/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(put("/api/customers/{id}", id)
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));

        mockMvc.perform(get("/api/customers/{id}", id).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("인증고객"))
                .andExpect(jsonPath("$.customerType").value("기업"));
    }

    @Test
    void rejectsUnauthenticatedCustomerDetail() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void rejectsUnauthenticatedCustomerList() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void filtersOwnActiveCustomersByTypeKeywordAndPage() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        long individualHong = insertCustomer(seller.id(), "홍길동", "01011112222", "개인", "ACTIVE");
        long individualKim = insertCustomer(seller.id(), "김개인", "01022223333", "개인", "ACTIVE");
        long individualThird = insertCustomer(seller.id(), "이개인", "01033334444", "개인", "ACTIVE");
        long corporateHong = insertCustomer(seller.id(), "홍길동상사", "01011119999", "기업", "ACTIVE");
        insertCustomer(seller.id(), "비활성개인", "01055556666", "개인", "INACTIVE");
        long foreignIndividual = insertCustomer(other.id(), "홍길동", "01011112222", "개인", "ACTIVE");

        mockMvc.perform(get("/api/customers").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items", hasSize(4)));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", " 개인 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(individualHong)))
                .andExpect(jsonPath("$.items[0].customerType").value("개인"))
                .andExpect(jsonPath("$.items[1].id").value(jsonId(individualKim)))
                .andExpect(jsonPath("$.items[2].id").value(jsonId(individualThird)));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", "기업"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(corporateHong)))
                .andExpect(jsonPath("$.items[0].name").value("홍길동상사"))
                .andExpect(jsonPath("$.items[0].customerType").value("기업"));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", "개인")
                        .param("keyword", "홍길동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(individualHong)));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", "기업")
                        .param("keyword", "홍길동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(corporateHong)));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", "개인")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(individualHong)))
                .andExpect(jsonPath("$.items[1].id").value(jsonId(individualKim)));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", "개인")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(individualThird)));

        MvcResult sellerIndividuals = mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", "개인"))
                .andExpect(status().isOk())
                .andReturn();
        assertFalseContains(
                sellerIndividuals.getResponse().getContentAsString(StandardCharsets.UTF_8),
                foreignIndividual
        );

        mockMvc.perform(get("/api/customers")
                        .session(other.session())
                        .param("customerType", "개인"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(jsonId(foreignIndividual)));

        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", "COMPANY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("고객 유형은 개인 또는 기업만 선택할 수 있습니다."));
        mockMvc.perform(get("/api/customers")
                        .session(seller.session())
                        .param("customerType", "BUSINESS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("고객 유형은 개인 또는 기업만 선택할 수 있습니다."));
    }

    @Test
    void rejectsCustomerTypesOtherThanIndividualAndCorporate() throws Exception {
        SalesMember seller = loginActiveSales();
        long id = insertCustomer(seller.id(), "유지고객", "01011112222", "기업", "ACTIVE");
        String rejected = """
                {"name":"변경","customerType":"%s"}
                """;

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejected.formatted("COMPANY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("고객 유형은 개인 또는 기업만 선택할 수 있습니다."));
        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejected.formatted("BUSINESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("고객 유형은 개인 또는 기업만 선택할 수 있습니다."));
        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejected.formatted("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("고객 유형은 개인 또는 기업만 선택할 수 있습니다."));
        mockMvc.perform(put("/api/customers/{id}", id)
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejected.formatted("COMPANY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("고객 유형은 개인 또는 기업만 선택할 수 있습니다."));

        mockMvc.perform(get("/api/customers/{id}", id).session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("유지고객"))
                .andExpect(jsonPath("$.customerType").value("기업"));
        assertEquals(1, customerCount(seller.id()));
    }

    @Test
    void deactivatesOwnCustomerAndHidesTheCustomerFromTheList() throws Exception {
        SalesMember seller = loginActiveSales();
        MvcResult created = mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .session(seller.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"비활성대상","customerType":"개인","phone":"01011112222","email":"off@example.com","address":"서울시","memo":"유지"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        mockMvc.perform(post("/api/customers/{id}/deactivate", id.longValue())
                        .with(csrf())
                        .session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(id.longValue())))
                .andExpect(jsonPath("$.name").value("비활성대상"))
                .andExpect(jsonPath("$.customerType").value("개인"))
                .andExpect(jsonPath("$.phone").value("01011112222"))
                .andExpect(jsonPath("$.email").value("off@example.com"))
                .andExpect(jsonPath("$.address").value("서울시"))
                .andExpect(jsonPath("$.memo").value("유지"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.memberId").doesNotExist());

        StoredCustomer stored = storedCustomer(id.longValue());
        assertEquals(seller.id(), stored.memberId());
        assertEquals("INACTIVE", stored.status());
        assertEquals(1, customerCount(seller.id()));

        mockMvc.perform(get("/api/customers").session(seller.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void rejectsDeactivateForAnotherMemberMissingAndAlreadyInactiveCustomers() throws Exception {
        SalesMember seller = loginActiveSales();
        SalesMember other = loginActiveSales();
        long activeId = insertCustomer(seller.id(), "유지고객", "01011112222", "기업", "ACTIVE");
        long inactiveId = insertCustomer(seller.id(), "이미비활성", "01033334444", "개인", "INACTIVE");

        mockMvc.perform(post("/api/customers/{id}/deactivate", activeId)
                        .with(csrf())
                        .session(other.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ACCESS_DENIED"))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.customerType").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
        assertEquals("ACTIVE", storedCustomer(activeId).status());

        mockMvc.perform(post("/api/customers/{id}/deactivate", 999999999L)
                        .with(csrf())
                        .session(seller.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("고객을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.name").doesNotExist());

        mockMvc.perform(post("/api/customers/{id}/deactivate", inactiveId)
                        .with(csrf())
                        .session(seller.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ALREADY_INACTIVE"))
                .andExpect(jsonPath("$.message").value("활성 고객만 비활성화할 수 있습니다."))
                .andExpect(jsonPath("$.name").doesNotExist());
        assertEquals("INACTIVE", storedCustomer(inactiveId).status());
        assertEquals(2, customerCount(seller.id()));
    }

    @Test
    void rejectsUnauthenticatedAndMissingCsrfWhenDeactivatingCustomer() throws Exception {
        SalesMember seller = loginActiveSales();
        long id = insertCustomer(seller.id(), "인증고객", "01011112222", "개인", "ACTIVE");

        mockMvc.perform(post("/api/customers/{id}/deactivate", id).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/customers/{id}/deactivate", id).session(seller.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertEquals("ACTIVE", storedCustomer(id).status());
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

    private StoredCustomer storedCustomer(long customerId) {
        return jdbcTemplate.queryForObject("""
                SELECT member_id, status
                FROM customer
                WHERE id = ?
                """, (rs, rowNum) -> new StoredCustomer(rs.getLong("member_id"), rs.getString("status")), customerId);
    }

    private int customerCount(long memberId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE member_id = ?",
                Integer.class,
                memberId
        );
        return count == null ? 0 : count;
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

    private record StoredCustomer(long memberId, String status) {
    }
}
