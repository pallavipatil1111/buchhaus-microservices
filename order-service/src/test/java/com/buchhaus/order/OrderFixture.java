package com.buchhaus.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Carts and orders in memory behind mocked repositories, plus a fake catalogue. */
final class OrderFixture {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T06:00:00Z"), ZoneOffset.UTC);

    final CartItemRepository cartItems = mock(CartItemRepository.class);
    final OrderRepository orders = mock(OrderRepository.class);
    final FakeCatalog catalog = new FakeCatalog();
    final List<CartItem> cart = new ArrayList<>();
    final List<CustomerOrder> orderRows = new ArrayList<>();
    final AtomicInteger ids = new AtomicInteger();
    /** Set to make saving an order fail, as if the database went away mid-checkout. */
    RuntimeException failSave;

    /** Runs the work directly: the repositories are in memory. */
    final LocalTx tx = new LocalTx() {
        @Override
        public <T> T run(Supplier<T> work) {
            return work.get();
        }
    };

    OrderFixture() {
        catalog.book(13L, "Clean Code", "799.00", 12);
        catalog.book(20L, "Ikigai", "350.00", 2);
        when(cartItems.findByUserIdOrderByIdAsc(any())).thenAnswer(inv -> cart.stream()
                .filter(c -> c.getUserId().equals(inv.getArgument(0))).toList());
        when(cartItems.findByUserIdAndBookId(any(), any())).thenAnswer(inv -> cart.stream()
                .filter(c -> c.getUserId().equals(inv.getArgument(0)) && c.getBookId().equals(inv.getArgument(1))).findFirst());
        when(cartItems.save(any())).thenAnswer(inv -> {
            CartItem c = inv.getArgument(0);
            if (!cart.contains(c)) {
                cart.add(c);
            }
            return c;
        });
        when(orders.save(any())).thenAnswer(inv -> {
            if (failSave != null) {
                throw failSave;
            }
            CustomerOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId((long) orderRows.size() + 1);
                orderRows.add(o);
            }
            return o;
        });
        when(orders.findById(any())).thenAnswer(inv -> orderRows.stream().filter(o -> o.getId().equals(inv.getArgument(0))).findFirst());
        when(orders.findByIdAndUserId(any(), any())).thenAnswer(inv -> orderRows.stream()
                .filter(o -> o.getId().equals(inv.getArgument(0)) && o.getUserId().equals(inv.getArgument(1))).findFirst());
        when(orders.findTop50ByStockConfirmedFalseAndStatusNotOrderByIdAsc(any())).thenAnswer(inv -> orderRows.stream()
                .filter(o -> !o.isStockConfirmed() && o.getStatus() != inv.getArgument(0)).toList());
    }

    void inCart(long userId, long bookId, int qty) {
        cart.add(new CartItem(userId, bookId, qty));
    }

    void emptyCartOf(long userId) {
        cart.removeIf(c -> c.getUserId() == userId);
    }

    OrderService orderService() {
        return new OrderService(orders, cartItems, catalog, tx, CLOCK, () -> "res-" + ids.incrementAndGet());
    }

    CartService cartService() {
        return new CartService(cartItems, catalog, tx);
    }
}
