package com.buchhaus.order.catalog;

import java.math.BigDecimal;
import java.util.List;

/** The catalogue-service's JSON, as this service reads it. Copies of its records, not a shared library. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record BookView(Long id, String sku, String title, String author, String genre, String description,
                           BigDecimal price, int stock, Integer publishedYear, Integer pages, boolean active,
                           long version) {
    }

    public record ReserveLine(Long bookId, Integer quantity) {
    }

    public record ReserveRequest(String reservationId, List<ReserveLine> lines) {
    }

    public record ReservedLine(Long bookId, String title, long unitPricePaise, int quantity) {
    }

    public record ReservationView(String reservationId, String status, List<ReservedLine> lines) {
    }

    public record CatalogSummary(long activeTitles, List<BookView> lowStock) {
    }
}
