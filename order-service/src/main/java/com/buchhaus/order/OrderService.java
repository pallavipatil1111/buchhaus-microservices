package com.buchhaus.order;

import com.buchhaus.common.ApiException;
import com.buchhaus.common.Pricing;
import com.buchhaus.order.Dtos.CheckoutRequest;
import com.buchhaus.order.Dtos.OrderView;
import com.buchhaus.order.catalog.Catalog;
import com.buchhaus.order.catalog.CatalogDtos.ReservationView;
import com.buchhaus.order.catalog.CatalogDtos.ReserveLine;
import com.buchhaus.order.catalog.CatalogDtos.ReserveRequest;
import com.buchhaus.order.catalog.CatalogDtos.ReservedLine;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checkout across two services, as a saga (no distributed transaction):
 * <ol>
 *   <li>Reserve the copies in the catalogue under a new reservation id. The catalogue locks the rows,
 *       checks stock and returns title and price snapshots. Sold out: 409, nothing changed.</li>
 *   <li>Save the order and empty the cart in one local transaction. If that fails, release the
 *       reservation (compensation) so the copies go back on the shelf.</li>
 *   <li>Confirm the reservation. If the catalogue is unreachable at that moment, the order is
 *       already safe; {@link ConfirmRetrier} confirms it later.</li>
 * </ol>
 * Cancelling releases the reservation first and only then marks the order cancelled; release is
 * idempotent, so a retried cancel never restocks twice.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final CartItemRepository cartItems;
    private final Catalog catalog;
    private final LocalTx tx;
    private final Clock clock;
    private final Supplier<String> reservationIds;

    public OrderService(OrderRepository orders, CartItemRepository cartItems, Catalog catalog, LocalTx tx, Clock clock,
                        Supplier<String> reservationIds) {
        this.orders = orders;
        this.cartItems = cartItems;
        this.catalog = catalog;
        this.tx = tx;
        this.clock = clock;
        this.reservationIds = reservationIds;
    }

    public OrderView checkout(Long userId, String email, CheckoutRequest shipping) {
        List<CartItem> cart = tx.run(() -> cartItems.findByUserIdOrderByIdAsc(userId));
        if (cart.isEmpty()) {
            throw ApiException.badRequest("Your cart is empty");
        }
        String reservationId = reservationIds.get();
        ReservationView reserved = catalog.reserve(new ReserveRequest(reservationId,
                cart.stream().map(c -> new ReserveLine(c.getBookId(), c.getQuantity())).toList()));

        CustomerOrder saved;
        try {
            saved = tx.run(() -> {
                CustomerOrder order = new CustomerOrder(userId, email.toLowerCase(Locale.ROOT), reservationId,
                        shipping.name().trim(), shipping.phone(), shipping.address().trim(), shipping.city().trim(),
                        shipping.pincode());
                long subtotal = 0;
                for (ReservedLine line : reserved.lines()) {
                    order.addItem(line.bookId(), line.title(), line.unitPricePaise(), line.quantity());
                    subtotal += Pricing.lineTotal(line.unitPricePaise(), line.quantity());
                }
                order.setTotals(subtotal, Pricing.shipping(subtotal));
                CustomerOrder o = orders.save(order);
                cartItems.deleteAllForUser(userId);
                return o;
            });
        } catch (RuntimeException e) {
            releaseQuietly(reservationId);
            throw e;
        }
        confirm(saved);
        return OrderView.of(saved);
    }

    public List<OrderView> mine(Long userId) {
        return tx.run(() -> orders.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().map(OrderView::of).toList());
    }

    public OrderView get(Long userId, Long orderId) {
        return tx.run(() -> OrderView.of(owned(userId, orderId)));
    }

    /** Customers can cancel until the order ships; the copies go back on the shelf. */
    public OrderView cancel(Long userId, Long orderId) {
        CustomerOrder order = tx.run(() -> owned(userId, orderId));
        if (!order.getStatus().customerCanCancel()) {
            throw ApiException.conflict("This order has already " + order.getStatus().name().toLowerCase(Locale.ROOT)
                    + " and can't be cancelled");
        }
        return transition(orderId, OrderStatus.CANCELLED);
    }

    // ---------- admin ----------

    public List<OrderView> all(OrderStatus status) {
        return tx.run(() -> (status == null ? orders.findAllByOrderByCreatedAtDescIdDesc()
                : orders.findByStatusOrderByCreatedAtDescIdDesc(status)).stream().map(OrderView::of).toList());
    }

    public OrderView updateStatus(Long orderId, OrderStatus target) {
        return transition(orderId, target);
    }

    /** Confirms reservations the catalogue missed during an outage. Returns how many were confirmed. */
    public int confirmPending() {
        int confirmed = 0;
        for (CustomerOrder order : tx.run(() -> orders.findTop50ByStockConfirmedFalseAndStatusNotOrderByIdAsc(OrderStatus.CANCELLED))) {
            if (confirm(order)) {
                confirmed++;
            }
        }
        return confirmed;
    }

    private OrderView transition(Long orderId, OrderStatus target) {
        CustomerOrder current = tx.run(() -> orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order")));
        if (!current.getStatus().canMoveTo(target)) {
            throw ApiException.conflict("Can't move an order from " + current.getStatus() + " to " + target);
        }
        if (target == OrderStatus.CANCELLED) {
            catalog.release(current.getReservationId()); // 503 if the catalogue is down: the order stays as it was
        }
        return tx.run(() -> {
            CustomerOrder order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
            if (!order.getStatus().canMoveTo(target)) {
                throw ApiException.conflict("Can't move an order from " + order.getStatus() + " to " + target);
            }
            order.moveTo(target, clock.instant());
            return OrderView.of(orders.save(order));
        });
    }

    private boolean confirm(CustomerOrder order) {
        try {
            catalog.confirm(order.getReservationId());
        } catch (RuntimeException e) {
            log.warn("Could not confirm reservation {} for order {} yet: {}", order.getReservationId(), order.getId(),
                    e.getMessage());
            return false;
        }
        tx.run(() -> {
            orders.findById(order.getId()).ifPresent(o -> {
                o.markStockConfirmed();
                orders.save(o);
            });
            return null;
        });
        order.markStockConfirmed();
        return true;
    }

    private void releaseQuietly(String reservationId) {
        try {
            catalog.release(reservationId);
        } catch (RuntimeException e) {
            // The catalogue's sweeper releases unconfirmed reservations after a timeout anyway.
            log.warn("Could not release reservation {}: {}", reservationId, e.getMessage());
        }
    }

    private CustomerOrder owned(Long userId, Long orderId) {
        return orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiException.notFound("Order"));
    }
}
