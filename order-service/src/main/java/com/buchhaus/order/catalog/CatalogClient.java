package com.buchhaus.order.catalog;

import com.buchhaus.order.catalog.CatalogDtos.BookView;
import com.buchhaus.order.catalog.CatalogDtos.CatalogSummary;
import com.buchhaus.order.catalog.CatalogDtos.ReservationView;
import com.buchhaus.order.catalog.CatalogDtos.ReserveRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** Declarative HTTP client; "catalog-service" is resolved through Eureka and load-balanced. */
@FeignClient(name = "catalog-service", path = "/internal")
public interface CatalogClient {

    @GetMapping("/books")
    List<BookView> books(@RequestParam("ids") List<Long> ids);

    @GetMapping("/summary")
    CatalogSummary summary();

    @PostMapping("/reservations")
    ReservationView reserve(@RequestBody ReserveRequest request);

    @PostMapping("/reservations/{id}/confirm")
    ReservationView confirm(@PathVariable("id") String reservationId);

    @PostMapping("/reservations/{id}/release")
    void release(@PathVariable("id") String reservationId);
}
