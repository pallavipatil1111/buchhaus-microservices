package com.buchhaus.catalog;

import com.buchhaus.common.Pricing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public final class Dtos {

    private Dtos() {
    }

    public record BookView(Long id, String sku, String title, String author, String genre, String description,
                           BigDecimal price, int stock, Integer publishedYear, Integer pages, boolean active,
                           long version) {

        public static BookView of(Book b) {
            return new BookView(b.getId(), b.getSku(), b.getTitle(), b.getAuthor(), b.getGenre(), b.getDescription(),
                    Pricing.rupees(b.getPricePaise()), b.getStock(), b.getPublishedYear(), b.getPages(), b.isActive(),
                    b.getVersion());
        }
    }

    /** Admin create/update. {@code version} must echo the value read, so concurrent edits are detected. */
    public record BookRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,20}$", message = "use 3-20 capital letters, digits or dashes") String sku,
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 120) String author,
            @NotBlank @Size(max = 40) String genre,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") BigDecimal price,
            @NotNull @Min(0) @Max(100000) Integer stock,
            @Min(1450) @Max(2100) Integer publishedYear,
            @Min(1) @Max(10000) Integer pages,
            Long version) {
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <E, T> PageView<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageView<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    // ---------- internal API used by the order-service ----------

    public record ReserveLine(@NotNull Long bookId, @NotNull @Min(1) @Max(10) Integer quantity) {
    }

    public record ReserveRequest(
            @NotBlank @Pattern(regexp = "^[0-9a-f-]{36}$", message = "must be a UUID") String reservationId,
            @NotEmpty @Size(max = 50) List<@Valid ReserveLine> lines) {
    }

    public record ReservedLine(Long bookId, String title, long unitPricePaise, int quantity) {
    }

    public record ReservationView(String reservationId, ReservationStatus status, List<ReservedLine> lines) {

        public static ReservationView of(Reservation r) {
            return new ReservationView(r.getId(), r.getStatus(), r.getLines().stream()
                    .map(l -> new ReservedLine(l.getBookId(), l.getTitleSnapshot(), l.getUnitPricePaise(), l.getQuantity()))
                    .toList());
        }
    }

    public record CatalogSummary(long activeTitles, List<BookView> lowStock) {
    }
}
