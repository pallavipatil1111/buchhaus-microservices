package com.buchhaus.order;

import com.buchhaus.common.Pricing;
import com.buchhaus.order.catalog.CatalogDtos.BookView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Same JSON as the monolith, so the web front end did not change. */
public final class Dtos {

    private Dtos() {
    }

    public record CartLine(BookView book, int quantity, BigDecimal lineTotal, boolean inStock) {
    }

    /** {@code freeShippingGap}: how much more to add for free shipping (0 once qualified). */
    public record CartView(List<CartLine> items, int itemCount, BigDecimal subtotal, BigDecimal shipping,
                           BigDecimal total, BigDecimal freeShippingGap, boolean canCheckout) {
    }

    /** 0 removes the book from the cart. */
    public record QuantityRequest(@NotNull @Min(0) @Max(10) Integer quantity) {
    }

    public record CheckoutRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number") String phone,
            @NotBlank @Size(max = 200) String address,
            @NotBlank @Size(max = 60) String city,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "must be a 6-digit PIN code") String pincode) {
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }

    public record OrderView(Long id, OrderStatus status, List<Line> items, BigDecimal subtotal, BigDecimal shipping,
                            BigDecimal total, Shipping shipTo, String customerEmail, boolean canCancel,
                            List<OrderStatus> nextStatuses, Instant createdAt, Instant updatedAt) {

        public record Line(Long bookId, String title, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        }

        public record Shipping(String name, String phone, String address, String city, String pincode) {
        }

        public static OrderView of(CustomerOrder o) {
            List<Line> lines = o.getItems().stream().map(i -> new Line(i.getBookId(), i.getTitleSnapshot(),
                    Pricing.rupees(i.getUnitPricePaise()), i.getQuantity(), Pricing.rupees(i.getLineTotalPaise()))).toList();
            return new OrderView(o.getId(), o.getStatus(), lines, Pricing.rupees(o.getSubtotalPaise()),
                    Pricing.rupees(o.getShippingPaise()), Pricing.rupees(o.getTotalPaise()),
                    new Shipping(o.getShipName(), o.getShipPhone(), o.getShipAddress(), o.getShipCity(), o.getShipPincode()),
                    o.getCustomerEmail(), o.getStatus().customerCanCancel(),
                    o.getStatus().next().stream().sorted().toList(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }

    /**
     * Admin dashboard. Order figures are local; {@code activeTitles} and {@code lowStock} come from
     * the catalogue and are null/empty with {@code catalogueAvailable = false} while it is down.
     */
    public record AdminStats(BigDecimal revenue, long orders, Map<OrderStatus, Long> ordersByStatus, Long activeTitles,
                             List<BookView> lowStock, List<TopSeller> topSellers, boolean catalogueAvailable) {
    }
}
