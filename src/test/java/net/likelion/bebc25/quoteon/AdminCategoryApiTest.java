package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
import net.likelion.bebc25.quoteon.support.ActiveSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCategoryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void existingCategoryListKeepsResponseAndOrder() throws Exception {
        MockHttpSession session = ActiveSession.open(mockMvc, jdbcTemplate);

        mockMvc.perform(get("/api/categories").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("전자제품"))
                .andExpect(jsonPath("$[0].parentId").value(nullValue()))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("사무용품"))
                .andExpect(jsonPath("$[1].parentId").value(nullValue()))
                .andExpect(jsonPath("$[2].id").value(3))
                .andExpect(jsonPath("$[2].name").value("가구"))
                .andExpect(jsonPath("$[2].parentId").value(nullValue()));
    }

    @Test
    void adminCanCreateRootAndChildCategories() throws Exception {
        MockHttpSession admin = loginAdmin();
        int quotes = count("quote");
        int products = count("product");

        MvcResult root = mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("  현관  ", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("현관"))
                .andExpect(jsonPath("$.parentId").value(nullValue()))
                .andReturn();
        long rootId = idOf(root);

        MvcResult child = mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("신발장", rootId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("신발장"))
                .andExpect(jsonPath("$.parentId").value(jsonId(rootId)))
                .andReturn();
        long childId = idOf(child);

        mockMvc.perform(get("/api/categories").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("전자제품"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("사무용품"))
                .andExpect(jsonPath("$[2].id").value(3))
                .andExpect(jsonPath("$[2].name").value("가구"))
                .andExpect(jsonPath("$[3].id").value(jsonId(rootId)))
                .andExpect(jsonPath("$[3].name").value("현관"))
                .andExpect(jsonPath("$[3].parentId").value(nullValue()))
                .andExpect(jsonPath("$[4].id").value(jsonId(childId)))
                .andExpect(jsonPath("$[4].name").value("신발장"))
                .andExpect(jsonPath("$[4].parentId").value(jsonId(rootId)));
        assertEquals(products, count("product"));
        assertEquals(quotes, count("quote"));
    }

    @Test
    void createRejectsInvalidNameAndMissingParent() throws Exception {
        MockHttpSession admin = loginAdmin();

        mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(" ", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("카테고리명을 입력해 주세요."));
        mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("가".repeat(101), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("카테고리명은 100자 이하로 입력해 주세요."));
        mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("가구", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 카테고리명입니다."));
        mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("없는상위", 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("상위 카테고리를 찾을 수 없습니다."));
        assertEquals(3, count("category"));
    }

    @Test
    void adminCanRenameAndMoveCategoryWithoutCycles() throws Exception {
        MockHttpSession admin = loginAdmin();
        long entrance = create(admin, "현관", null);
        long living = create(admin, "거실", null);
        long shoe = create(admin, "신발장", entrance);
        long sofa = create(admin, "소파", living);

        mockMvc.perform(put("/api/admin/categories/" + shoe)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("  신발장 변경  ", living)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(shoe)))
                .andExpect(jsonPath("$.name").value("신발장 변경"))
                .andExpect(jsonPath("$.parentId").value(jsonId(living)));

        mockMvc.perform(put("/api/admin/categories/" + entrance)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("현관", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("현관"))
                .andExpect(jsonPath("$.parentId").value(nullValue()));

        mockMvc.perform(put("/api/admin/categories/" + missingId())
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("없는카테고리", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("카테고리를 찾을 수 없습니다."));
        mockMvc.perform(put("/api/admin/categories/" + living)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("거실", living)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_INVALID_PARENT"))
                .andExpect(jsonPath("$.message").value("자기 자신을 상위 카테고리로 지정할 수 없습니다."));
        mockMvc.perform(put("/api/admin/categories/" + living)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("거실", sofa)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_INVALID_PARENT"))
                .andExpect(jsonPath("$.message").value("하위 카테고리를 상위 카테고리로 지정할 수 없습니다."));
        mockMvc.perform(put("/api/admin/categories/" + sofa)
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("현관", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_EXISTS"));

        assertEquals(living, parentId(sofa));
        assertEquals("소파", nameOf(sofa));
        assertEquals(null, parentId(living));
    }

    @Test
    void deleteRemovesOnlyLeafWithoutProducts() throws Exception {
        MockHttpSession admin = loginAdmin();
        long parent = create(admin, "서재", null);
        long child = create(admin, "책상", parent);
        int products = count("product");
        int quotes = count("quote");

        mockMvc.perform(delete("/api/admin/categories/" + parent).with(csrf()).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_CHILDREN"))
                .andExpect(jsonPath("$.message").value("하위 카테고리가 있어 삭제할 수 없습니다."));
        assertEquals("서재", nameOf(parent));
        assertEquals("책상", nameOf(child));

        String productCode = "CAT-" + token();
        jdbcTemplate.update(
                """
                INSERT INTO product (category_id, product_code, name, unit_price, sale_price, status)
                VALUES (?, ?, '연결제품', 1000.00, 1500.00, 'ACTIVE')
                """,
                child,
                productCode
        );
        mockMvc.perform(delete("/api/admin/categories/" + child).with(csrf()).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_PRODUCTS"))
                .andExpect(jsonPath("$.message").value("연결된 제품이 있어 삭제할 수 없습니다."));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product WHERE product_code = ?",
                Integer.class,
                productCode
        ));
        assertEquals(child, jdbcTemplate.queryForObject(
                "SELECT category_id FROM product WHERE product_code = ?",
                Long.class,
                productCode
        ));

        jdbcTemplate.update("DELETE FROM product WHERE product_code = ?", productCode);
        mockMvc.perform(delete("/api/admin/categories/" + child).with(csrf()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jsonId(child)))
                .andExpect(jsonPath("$.name").value("책상"))
                .andExpect(jsonPath("$.parentId").value(jsonId(parent)));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category WHERE id = ?",
                Integer.class,
                child
        ));
        assertEquals("서재", nameOf(parent));

        mockMvc.perform(delete("/api/admin/categories/1").with(csrf()).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_PRODUCTS"));
        assertEquals("전자제품", nameOf(1));
        assertEquals(products, count("product"));
        assertEquals(quotes, count("quote"));

        mockMvc.perform(delete("/api/admin/categories/" + missingId()).with(csrf()).session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("카테고리를 찾을 수 없습니다."));
    }

    @Test
    void categoryChangesRequireAdminAndCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("미인증", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(put("/api/admin/categories/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("미인증", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(delete("/api/admin/categories/1").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        MockHttpSession sales = ActiveSession.open(mockMvc, jdbcTemplate);
        mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("일반회원", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/admin/categories/1")
                        .with(csrf())
                        .session(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("일반회원", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/admin/categories/1").with(csrf()).session(sales))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MockHttpSession admin = loginAdmin();
        mockMvc.perform(post("/api/admin/categories")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("토큰없음", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        mockMvc.perform(put("/api/admin/categories/1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("토큰없음", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        mockMvc.perform(delete("/api/admin/categories/1").session(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF"));
        assertEquals("전자제품", nameOf(1));
        assertEquals(3, count("category"));
    }

    private long create(MockHttpSession admin, String name, Long parentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/categories")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(name, parentId)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private MockHttpSession loginAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"ADMIN","name":"카테고리관리자","email":"%s","password":"password1234","phone":"01012345678","companyName":"관리회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(signup.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        int updated = jdbcTemplate.update(
                "UPDATE member SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'",
                id.longValue()
        );
        if (updated != 1) {
            throw new IllegalStateException("test admin was not activated");
        }
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private static String categoryJson(String name, Long parentId) {
        String parent = parentId == null ? "null" : Long.toString(parentId);
        return """
                {"name":"%s","parentId":%s}
                """.formatted(name, parent);
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private Long parentId(long id) {
        return jdbcTemplate.query(
                "SELECT parent_id FROM category WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    long parentId = rs.getLong("parent_id");
                    return rs.wasNull() ? null : parentId;
                },
                id
        );
    }

    private String nameOf(long id) {
        return jdbcTemplate.queryForObject("SELECT name FROM category WHERE id = ?", String.class, id);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private long missingId() {
        Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM category", Long.class);
        return (maxId == null ? 0L : maxId) + 100_000L;
    }

    private static int jsonId(long id) {
        return Math.toIntExact(id);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
