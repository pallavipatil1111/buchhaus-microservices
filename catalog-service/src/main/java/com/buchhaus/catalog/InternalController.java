package com.buchhaus.catalog;

import com.buchhaus.catalog.Dtos.BookView;
import com.buchhaus.catalog.Dtos.CatalogSummary;
import com.buchhaus.catalog.Dtos.ReservationView;
import com.buchhaus.catalog.Dtos.ReserveRequest;
import com.buchhaus.common.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API for the order-service. The gateway has no route to /internal, so these
 * endpoints are only reachable inside the Docker network.
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    static final int MAX_IDS = 100;

    private final BookService books;
    private final StockService stock;

    public InternalController(BookService books, StockService stock) {
        this.books = books;
        this.stock = stock;
    }

    @GetMapping("/books")
    public List<BookView> books(@RequestParam List<Long> ids) {
        if (ids.size() > MAX_IDS) {
            throw ApiException.badRequest("At most " + MAX_IDS + " ids per request");
        }
        return books.byIds(ids);
    }

    @GetMapping("/summary")
    public CatalogSummary summary() {
        return books.summary();
    }

    @PostMapping("/reservations")
    public ReservationView reserve(@Valid @RequestBody ReserveRequest request) {
        return stock.reserve(request);
    }

    @PostMapping("/reservations/{id}/confirm")
    public ReservationView confirm(@PathVariable String id) {
        return stock.confirm(id);
    }

    @PostMapping("/reservations/{id}/release")
    public ResponseEntity<ReservationView> release(@PathVariable String id) {
        return stock.release(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
