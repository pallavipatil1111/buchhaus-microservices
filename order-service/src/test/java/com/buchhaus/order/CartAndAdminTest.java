package com.buchhaus.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.buchhaus.common.ApiException;
import com.buchhaus.order.Dtos.AdminStats;
import com.buchhaus.order.Dtos.CartView;
import com.buchhaus.order.catalog.CatalogErrors;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CartAndAdminTest {

    private OrderFixture f;
    private CartService cart;

    @BeforeEach
    void setUp() {
        f = new OrderFixture();
        cart = f.cartService();
    }

    @Test
    void cartShowsFreshCataloguePricesAndTheFreeShippingGap() {
        CartView view = cart.setQuantity(1L, 20L, 1);
        assertThat(view.itemCount()).isEqualTo(1);
        assertThat(view.subtotal()).isEqualByComparingTo(new BigDecimal("350"));
        assertThat(view.shipping()).isEqualByComparingTo(new BigDecimal("49"));
        assertThat(view.freeShippingGap()).isEqualByComparingTo(new BigDecimal("149"));
        assertThat(view.items().get(0).book().title()).isEqualTo("Ikigai");
        assertThat(view.canCheckout()).isTrue();
    }

    @Test
    void quantityCannotExceedStockOrTen() {
        assertThatThrownBy(() -> cart.setQuantity(1L, 20L, 3)).hasMessage("Only 2 copies of \"Ikigai\" left");
        assertThatThrownBy(() -> cart.setQuantity(1L, 13L, 11)).hasMessageContaining("between 0 and 10");
        assertThatThrownBy(() -> cart.setQuantity(1L, 99L, 1)).hasMessage("Book not found");
    }

    @Test
    void booksDeletedFromTheCatalogueDropOutOfTheCart() {
        f.inCart(1L, 13L, 1);
        f.inCart(1L, 77L, 1); // no longer in the catalogue
        CartView view = cart.view(1L);
        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).book().id()).isEqualTo(13L);
    }

    @Test
    void anEmptyCartDoesNotCallTheCatalogue() {
        assertThat(cart.view(1L).items()).isEmpty();
        assertThat(f.catalog.calls).isEmpty();
    }

    @Test
    void adminDashboardStillLoadsWhenTheCatalogueIsDown() {
        AdminService admin = new AdminService(f.orders, f.catalog, f.tx);
        when(f.orders.topSellers(any(), any())).thenReturn(List.of(new TopSeller(13L, "Clean Code", 3L)));

        AdminStats up = admin.stats();
        assertThat(up.catalogueAvailable()).isTrue();
        assertThat(up.activeTitles()).isEqualTo(2L);

        f.catalog.down = true;
        AdminStats down = admin.stats();
        assertThat(down.catalogueAvailable()).isFalse();
        assertThat(down.activeTitles()).isNull();
        assertThat(down.lowStock()).isEmpty();
        assertThat(down.topSellers()).hasSize(1); // order figures are local and still shown
    }

    @Test
    void catalogueErrorsKeepTheirStatusAndMessage() {
        ApiException e = CatalogErrors.translate(409,
                "{\"status\":409,\"error\":\"Conflict\",\"message\":\"\\\"Ikigai\\\" just sold out\",\"fields\":{}}");
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getMessage()).isEqualTo("\"Ikigai\" just sold out");
        assertThat(CatalogErrors.translate(404, "").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(CatalogErrors.translate(499, null).getMessage()).isEqualTo("The catalogue rejected the request");
        // an open circuit breaker or exhausted retries become a friendly 503; business errors pass through
        assertThat(CatalogErrors.unavailable(new RuntimeException("Connection refused")).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(CatalogErrors.unavailable(e)).isSameAs(e);
    }
}
