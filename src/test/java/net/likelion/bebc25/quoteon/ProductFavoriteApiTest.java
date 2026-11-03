package net.likelion.bebc25.quoteon;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductFavoriteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void memberCanAddRemoveAndFilterOwnFavorites() throws Exception {
        MockHttpSession admin = loginAdmin();
        SalesMember first = loginActiveSales();
        SalesMember second = loginActiveSales();
        long productId = createProduct(admin, "즐겨찾기제품");

        mockMvc.perform(post("/api/products/{productId}/favorite", productId)
                        .with(csrf())
                        .session(first.session()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.favorited").value(true));

        mockMvc.perform(post("/api/products/{productId}/favorite", productId)
                        .with(csrf())
                        .session(first.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAVORITE_EXISTS"));

        mockMvc.perform(get("/api/products").session(first.session()).param("favoriteOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(productId))
                .andExpect(jsonPath("$.items[0].favorited").value(true));

        mockMvc.perform(get("/api/products").session(second.session()).param("favoriteOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/products/{productId}", productId).session(first.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true));
        mockMvc.perform(get("/api/products/{productId}", productId).session(second.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));

        mockMvc.perform(delete("/api/products/{productId}/favorite", productId)
                        .with(csrf())
                        .session(first.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false));

        mockMvc.perform(get("/api/products").session(first.session()).param("favoriteOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void favoriteRejectsMissingAndInactiveProducts() throws Exception {
        MockHttpSession admin = loginAdmin();
        SalesMember seller = loginActiveSales();
        long productId = createProduct(admin, "중지후즐겨찾기");

        mockMvc.perform(post("/api/products/{productId}/favorite", 9_999_999L)
                        .with(csrf())
                        .session(seller.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        mockMvc.perform(post("/api/admin/products/{productId}/stop", productId)
                        .with(csrf())
                        .session(admin))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/products/{productId}/favorite", productId)
                        .with(csrf())
                        .session(seller.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAVORITE_PRODUCT_UNAVAILABLE"));

        mockMvc.perform(delete("/api/products/{productId}/favorite", productId)
                        .with(csrf())
                        .session(seller.session()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAVORITE_NOT_FOUND"));
    }

    private MockHttpSession loginAdmin() throws Exception {
        String email = "fav-admin-" + UUID.randomUUID() + "@quoteon.test";
        mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"ADMIN","name":"즐겨찾기관리자","email":"%s","password":"password1234","phone":"01012345678","companyName":"관리회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE member SET status = 'ACTIVE' WHERE email = ?", email);
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

    private SalesMember loginActiveSales() throws Exception {
        String email = "fav-sales-" + UUID.randomUUID() + "@quoteon.test";
        MvcResult signup = mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountType":"SALES","name":"즐겨찾기영업","email":"%s","password":"password1234","phone":"01012345678","companyName":"영업회사"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idOf(signup);
        jdbcTemplate.update("UPDATE member SET status = 'ACTIVE' WHERE id = ?", id);
        MvcResult login = mockMvc.perform(post("/api/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new SalesMember(id, (MockHttpSession) login.getRequest().getSession(false));
    }

    private long createProduct(MockHttpSession admin, String name) throws Exception {
        long categoryId = jdbcTemplate.queryForObject("SELECT id FROM category ORDER BY id LIMIT 1", Long.class);
        MvcResult result = mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"%s","productCode":"%s","unitPrice":1000,"salePrice":2000}
                                """.formatted(categoryId, name, "FAV-" + UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
        return id.longValue();
    }

    private record SalesMember(long id, MockHttpSession session) {
    }
}
