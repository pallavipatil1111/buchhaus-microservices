package com.buchhaus.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.buchhaus.order.catalog.Catalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** order-service on its own (H2, no Eureka) with an in-memory catalogue in place of the Feign client. */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
@Import(OrderApiTest.FakeCatalogConfig.class)
class OrderApiTest {

    @TestConfiguration
    static class FakeCatalogConfig {

        @Bean
        @Primary
        FakeCatalog fakeCatalog() {
            FakeCatalog catalog = new FakeCatalog();
            catalog.book(13L, "Clean Code", "799.00", 12);
            catalog.book(20L, "Ikigai", "350.00", 2);
            return catalog;
        }
    }

    private static final Map<String, String> ADDRESS = Map.of("name", "Pallavi Patil", "phone", "9876543210",
            "address", "Flat 12, Baner Road", "city", "Pune", "pincode", "411045");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private Catalog catalog;

    @Test
    void cartToOrderToCancelThroughHttp() throws Exception {
        mvc.perform(put("/api/cart/items/13").header("X-User-Id", "7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.shipping").value(0));
        String order = mvc.perform(post("/api/orders").header("X-User-Id", "7").header("X-User-Email", "asha@example.com")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ADDRESS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerEmail").value("asha@example.com"))
                .andExpect(jsonPath("$.items[0].title").value("Clean Code"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(order).get("id").asLong();

        mvc.perform(get("/api/cart").header("X-User-Id", "7")).andExpect(jsonPath("$.itemCount").value(0));
        mvc.perform(get("/api/orders/" + id).header("X-User-Id", "8")).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/" + id + "/cancel").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void requestsThatDidNotComeThroughTheGatewayAreRefused() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stats").header("X-User-Role", "CUSTOMER")).andExpect(status().isForbidden());
    }

    @Test
    void checkoutValidatesTheAddressAndTheCatalogueOutageIsA503() throws Exception {
        mvc.perform(post("/api/orders").header("X-User-Id", "9").header("X-User-Email", "x@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "X", "phone", "12345", "address", "A", "city", "B",
                                "pincode", "000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phone").exists())
                .andExpect(jsonPath("$.fields.pincode").exists());

        mvc.perform(put("/api/cart/items/20").header("X-User-Id", "9").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}")).andExpect(status().isOk());
        ((FakeCatalog) catalog).down = true;
        try {
            mvc.perform(post("/api/orders").header("X-User-Id", "9").header("X-User-Email", "x@example.com")
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ADDRESS)))
                    .andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/admin/stats").header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.catalogueAvailable").value(false));
        } finally {
            ((FakeCatalog) catalog).down = false;
        }
    }
}
