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
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].productCode").value("SH-600"))
                .andExpect(jsonPath("$[0].name").value("화이트 신발장"))
                .andExpect(jsonPath("$[0].salePrice").value(120000.00))
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("전자제품"))
                .andExpect(jsonPath("$[0].imagePath").value("/images/products/sh-600.jpg"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].productCode").value("SH-800"))
                .andExpect(jsonPath("$[1].categoryId").value(1))
                .andExpect(jsonPath("$[1].salePrice").value(150000.00))
                .andExpect(jsonPath("$[2].productCode").value("OF-100"))
                .andExpect(jsonPath("$[2].categoryId").value(2))
                .andExpect(jsonPath("$[2].categoryName").value("사무용품"))
                .andExpect(jsonPath("$[2].salePrice").value(135000.00));
    }

    @Test
    void productKeywordMatchesCodeOrName() throws Exception {
        mockMvc.perform(get("/api/products")
                        .session(session)
                        .param("keyword", "SH-600")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].productCode").value("SH-600"));

        mockMvc.perform(get("/api/products")
                        .session(session)
                        .param("keyword", "신발")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("화이트 신발장"));
    }

    @Test
    void productKeywordIsBoundAsData() throws Exception {
        mockMvc.perform(get("/api/products")
                        .session(session)
                        .param("keyword", "' OR '1'='1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
