package com.buchhaus.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** catalog-service on its own (H2 with real row locks, no Eureka). */
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class CatalogApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private BookRepository books;

    private String reserve(String id, long bookId, int qty) throws Exception {
        return json.writeValueAsString(Map.of("reservationId", id,
                "lines", List.of(Map.of("bookId", bookId, "quantity", qty))));
    }

    private long newBook(String sku, int stock) throws Exception {
        String body = mvc.perform(post("/api/admin/books").header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sku", sku, "title", "Test " + sku, "author", "Tester",
                                "genre", "Testing", "price", "300.00", "stock", stock))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void catalogueIsSearchable() throws Exception {
        mvc.perform(get("/api/books").param("genre", "Classics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/books").param("q", "kalam"))
                .andExpect(jsonPath("$.content[0].title").value("Wings of Fire"));
        mvc.perform(get("/api/genres")).andExpect(status().isOk());
    }

    @Test
    void adminRoutesCheckTheRoleHeaderToo() throws Exception {
        mvc.perform(get("/api/admin/books").header("X-User-Role", "CUSTOMER")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/books")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/books").header("X-User-Role", "ADMIN")).andExpect(status().isOk());
    }

    @Test
    void reserveConfirmReleaseIsIdempotent() throws Exception {
        long id = newBook("IT-0001", 5);
        String rid = UUID.randomUUID().toString();
        mvc.perform(post("/internal/reservations").contentType(MediaType.APPLICATION_JSON).content(reserve(rid, id, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPricePaise").value(30000));
        mvc.perform(post("/internal/reservations").contentType(MediaType.APPLICATION_JSON).content(reserve(rid, id, 2)))
                .andExpect(status().isOk());
        assertThat(books.findById(id).orElseThrow().getStock()).isEqualTo(3);

        mvc.perform(post("/internal/reservations/" + rid + "/confirm")).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/internal/reservations/" + rid + "/release")).andExpect(jsonPath("$.status").value("RELEASED"));
        mvc.perform(post("/internal/reservations/" + rid + "/release")).andExpect(status().isOk());
        assertThat(books.findById(id).orElseThrow().getStock()).isEqualTo(5);
        mvc.perform(post("/internal/reservations/" + UUID.randomUUID() + "/release")).andExpect(status().isNoContent());
    }

    @Test
    void lastCopyIsSoldOnceUnderConcurrency() throws Exception {
        long id = newBook("IT-0002", 1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            attempts.add(() -> mvc.perform(post("/internal/reservations").contentType(MediaType.APPLICATION_JSON)
                    .content(reserve(UUID.randomUUID().toString(), id, 1))).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(attempts)) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(3);
        assertThat(books.findById(id).orElseThrow().getStock()).isZero();
    }

    @Test
    void soldBooksAreRetiredUnsoldBooksDeleted() throws Exception {
        long sold = newBook("IT-0003", 3);
        long unsold = newBook("IT-0004", 3);
        mvc.perform(post("/internal/reservations").contentType(MediaType.APPLICATION_JSON)
                .content(reserve(UUID.randomUUID().toString(), sold, 1))).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/books/" + sold).header("X-User-Role", "ADMIN"))
                .andExpect(jsonPath("$.result").value("retired"));
        mvc.perform(delete("/api/admin/books/" + unsold).header("X-User-Role", "ADMIN"))
                .andExpect(jsonPath("$.result").value("deleted"));
        mvc.perform(get("/api/books/" + sold)).andExpect(status().isNotFound());
    }
}
