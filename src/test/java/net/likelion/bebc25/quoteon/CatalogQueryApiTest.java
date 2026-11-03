package net.likelion.bebc25.quoteon;

import net.likelion.bebc25.quoteon.support.ActiveSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogQueryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockHttpSession session;

    @BeforeEach
    void loginAsActiveMember() throws Exception {
        session = ActiveSession.open(mockMvc, jdbcTemplate);
    }

    @Test
    void categoriesReturnParentAndChildFields() throws Exception {
        mockMvc.perform(get("/api/categories").session(session).accept(MediaType.APPLICATION_JSON))
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
    void productsReturnOnSaleRows() throws Exception {
        mockMvc.perform(get("/api/products").session(session).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.items[0].name").value("화이트 신발장"))
                .andExpect(jsonPath("$.items[0].salePrice").value(120000.00))
                .andExpect(jsonPath("$.items[0].categoryId").value(1))
                .andExpect(jsonPath("$.items[0].categoryName").value("전자제품"))
                .andExpect(jsonPath("$.items[0].imagePath").value("/images/products/sh-600.jpg"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[1].productCode").value("SH-800"))
                .andExpect(jsonPath("$.items[1].categoryId").value(1))
                .andExpect(jsonPath("$.items[1].salePrice").value(150000.00))
                .andExpect(jsonPath("$.items[2].productCode").value("OF-100"))
                .andExpect(jsonPath("$.items[2].categoryId").value(2))
                .andExpect(jsonPath("$.items[2].categoryName").value("사무용품"))
                .andExpect(jsonPath("$.items[2].salePrice").value(135000.00));
    }

    @Test
    void productKeywordMatchesCodeOrName() throws Exception {
        mockMvc.perform(get("/api/products")
                        .session(session)
                        .param("keyword", "SH-600")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"));

        mockMvc.perform(get("/api/products")
                        .session(session)
                        .param("keyword", "신발")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("화이트 신발장"));
    }

    @Test
    void productKeywordIsBoundAsData() throws Exception {
        mockMvc.perform(get("/api/products")
                        .session(session)
                        .param("keyword", "' OR '1'='1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void productsPageAndCategoryStayTogether() throws Exception {
        mockMvc.perform(get("/api/products")
                        .session(session)
                        .param("page", "0")
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/products")
                        .session(session)
                        .param("categoryId", "1")
                        .param("page", "1")
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productCode").value("SH-800"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/products").session(session).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/products").session(session).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하여야 합니다."));
    }

    @Test
    void onSaleProductDetailIncludesSpecificationPriceAndImages() throws Exception {
        mockMvc.perform(get("/api/products/{productId}", 1).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("SH-600"))
                .andExpect(jsonPath("$.name").value("화이트 신발장"))
                .andExpect(jsonPath("$.unitPrice").value(80000.00))
                .andExpect(jsonPath("$.salePrice").value(120000.00))
                .andExpect(jsonPath("$.specification").value("600x330x1828mm"))
                .andExpect(jsonPath("$.description").value("화이트 색상의 신발장"))
                .andExpect(jsonPath("$.imagePath").value("/images/products/sh-600.jpg"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.detailImages", hasSize(0)));

        jdbcTemplate.update("UPDATE product SET status = 'INACTIVE' WHERE id = 1");
        mockMvc.perform(get("/api/products/{productId}", 1).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(get("/api/products/{productId}", 999999).session(session))
                .andExpect(status().isNotFound());
    }
}
