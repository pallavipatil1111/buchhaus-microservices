package com.buchhaus.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.buchhaus.common.ApiException;
import com.buchhaus.order.Dtos.CheckoutRequest;
import com.buchhaus.order.Dtos.OrderView;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OrderServiceTest {

    private static final CheckoutRequest ADDRESS =
            new CheckoutRequest("Pallavi Patil", "9876543210", " Flat 12, Baner Road ", "Pune", "411045");

    private OrderFixture f;
    private OrderService service;

    @BeforeEach
    void setUp() {
        f = new OrderFixture();
        service = f.orderService();
    }

    @Test
    void checkoutReservesSavesTheOrderWithSnapshotsEmptiesTheCartAndConfirms() {
        f.inCart(1L, 13L, 2);
        f.inCart(1L, 20L, 1);

        OrderView order = service.checkout(1L, "Reader@BuchHaus.local", ADDRESS);

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.total()).isEqualByComparingTo(new BigDecimal("1948"));
        assertThat(order.shipping()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.items()).extracting(OrderView.Line::title).containsExactly("Clean Code", "Ikigai");
        assertThat(order.customerEmail()).isEqualTo("reader@buchhaus.local");
        assertThat(order.shipTo().address()).isEqualTo("Flat 12, Baner Road");
        assertThat(f.catalog.stock.get(13L)[0]).isEqualTo(10);
        assertThat(f.catalog.reservations).containsEntry("res-1", "CONFIRMED");
        assertThat(f.orderRows.get(0).isStockConfirmed()).isTrue();
        verify(f.cartItems).deleteAllForUser(1L);
        assertThat(f.catalog.calls).containsExactly("reserve", "confirm");
    }

    @Test
    void soldOutInTheCatalogueSavesNothing() {
        f.inCart(1L, 20L, 3);
        assertThatThrownBy(() -> service.checkout(1L, "r@x.in", ADDRESS))
                .isInstanceOf(ApiException.class)
                .hasMessage("\"Ikigai\" just sold out");
        assertThat(f.orderRows).isEmpty();
        verify(f.cartItems, never()).deleteAllForUser(any());
    }

    @Test
    void ifSavingTheOrderFailsTheReservationIsReleased() {
        f.inCart(1L, 13L, 2);
        // reserve succeeds, then the local transaction that saves the order blows up
        f.failSave = new IllegalStateException("database went away");

        assertThatThrownBy(() -> service.checkout(1L, "r@x.in", ADDRESS)).hasMessage("database went away");
        assertThat(f.catalog.reservations).containsEntry("res-1", "RELEASED");
        assertThat(f.catalog.stock.get(13L)[0]).isEqualTo(12);
    }

    @Test
    void anUnreachableCatalogueFailsCheckoutWithA503AndKeepsTheCart() {
        f.inCart(1L, 13L, 1);
        f.catalog.down = true;
        assertThatThrownBy(() -> service.checkout(1L, "r@x.in", ADDRESS))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasMessageContaining("Your cart is safe");
        assertThat(f.cart).hasSize(1);
        assertThat(f.orderRows).isEmpty();
    }

    @Test
    void ifConfirmingFailsTheOrderStillStandsAndIsConfirmedLater() {
        f.inCart(1L, 13L, 1);
        f.catalog.confirmDown = true;

        OrderView order = service.checkout(1L, "r@x.in", ADDRESS);

        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(f.orderRows.get(0).isStockConfirmed()).isFalse();
        assertThat(f.catalog.reservations).containsEntry("res-1", "RESERVED");

        f.catalog.confirmDown = false;
        assertThat(service.confirmPending()).isEqualTo(1);
        assertThat(f.orderRows.get(0).isStockConfirmed()).isTrue();
        assertThat(f.catalog.reservations).containsEntry("res-1", "CONFIRMED");
        assertThat(service.confirmPending()).isZero();
    }

    @Test
    void cancellingReleasesTheStockAndOnlyThenCancels() {
        f.inCart(1L, 20L, 2);
        OrderView order = service.checkout(1L, "r@x.in", ADDRESS);
        assertThat(f.catalog.stock.get(20L)[0]).isZero();

        OrderView cancelled = service.cancel(1L, order.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(f.catalog.stock.get(20L)[0]).isEqualTo(2);
        assertThat(f.catalog.reservations).containsEntry("res-1", "RELEASED");
    }

    @Test
    void ifTheCatalogueIsDownTheOrderIsNotCancelled() {
        f.inCart(1L, 13L, 1);
        OrderView order = service.checkout(1L, "r@x.in", ADDRESS);
        f.catalog.down = true;

        assertThatThrownBy(() -> service.cancel(1L, order.id())).isInstanceOf(ApiException.class);
        assertThat(f.orderRows.get(0).getStatus()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void customersCannotCancelShippedOrdersOrOtherPeoplesOrders() {
        f.inCart(1L, 13L, 1);
        OrderView order = service.checkout(1L, "r@x.in", ADDRESS);

        assertThatThrownBy(() -> service.cancel(2L, order.id())).hasMessage("Order not found");
        service.updateStatus(order.id(), OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.cancel(1L, order.id())).hasMessageContaining("already shipped");
        assertThat(f.catalog.calls).doesNotContain("release");
    }

    @Test
    void adminsFollowTheStateMachine() {
        f.inCart(1L, 13L, 1);
        OrderView order = service.checkout(1L, "r@x.in", ADDRESS);
        service.updateStatus(order.id(), OrderStatus.SHIPPED);
        assertThatThrownBy(() -> service.updateStatus(order.id(), OrderStatus.PLACED)).hasMessageContaining("Can't move");
        // admins may still cancel a shipped order; stock comes back
        assertThat(service.updateStatus(order.id(), OrderStatus.CANCELLED).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(f.catalog.stock.get(13L)[0]).isEqualTo(12);
        assertThatThrownBy(() -> service.updateStatus(order.id(), OrderStatus.DELIVERED)).isInstanceOf(ApiException.class);
    }

    @Test
    void anEmptyCartCannotBeCheckedOut() {
        assertThatThrownBy(() -> service.checkout(1L, "r@x.in", ADDRESS)).hasMessage("Your cart is empty");
        assertThat(f.catalog.calls).isEmpty();
    }
}
