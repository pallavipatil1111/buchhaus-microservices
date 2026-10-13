package com.buchhaus.catalog;

import static com.buchhaus.catalog.CatalogFixture.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.buchhaus.catalog.Dtos.ReservationView;
import com.buchhaus.common.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StockServiceTest {

    private static final String R1 = "11111111-1111-1111-1111-111111111111";
    private static final String R2 = "22222222-2222-2222-2222-222222222222";

    private CatalogFixture f;
    private StockService stock;

    @BeforeEach
    void setUp() {
        f = new CatalogFixture();
        stock = f.stock();
    }

    @Test
    void reservingTakesTheCopiesAndSnapshotsTitleAndPrice() {
        ReservationView r = stock.reserve(request(R1, 13L, 2, 20L, 1));

        assertThat(r.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(r.lines()).extracting(Dtos.ReservedLine::title).containsExactly("Clean Code", "Ikigai");
        assertThat(r.lines().get(0).unitPricePaise()).isEqualTo(799_00L);
        assertThat(f.cleanCode.getStock()).isEqualTo(10);
        assertThat(f.ikigai.getStock()).isEqualTo(1);
    }

    @Test
    void aRetriedReserveReturnsTheSameReservationWithoutTakingStockTwice() {
        stock.reserve(request(R1, 13L, 2));
        ReservationView again = stock.reserve(request(R1, 13L, 2));

        assertThat(again.reservationId()).isEqualTo(R1);
        assertThat(f.cleanCode.getStock()).isEqualTo(10);
        assertThat(f.reservationRows).hasSize(1);
    }

    @Test
    void notEnoughStockReservesNothingAtAll() {
        assertThatThrownBy(() -> stock.reserve(request(R1, 13L, 1, 20L, 3)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Only 2 copies of \"Ikigai\" left")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(f.reservations, never()).save(any());
    }

    @Test
    void theLastCopyCanOnlyBeReservedOnce() {
        stock.reserve(request(R1, 21L, 1));
        assertThatThrownBy(() -> stock.reserve(request(R2, 21L, 1))).hasMessage("\"The Alchemist\" just sold out");
        assertThat(f.lastCopy.getStock()).isZero();
    }

    @Test
    void retiredBooksCannotBeReserved() {
        f.ikigai.setActive(false);
        assertThatThrownBy(() -> stock.reserve(request(R1, 20L, 1))).hasMessageContaining("no longer sold");
    }

    @Test
    void duplicateLinesForOneBookAreAddedUp() {
        assertThatThrownBy(() -> stock.reserve(request(R1, 20L, 2, 20L, 1))).hasMessageContaining("Only 2 copies");
        stock.reserve(request(R2, 20L, 1, 20L, 1));
        assertThat(f.ikigai.getStock()).isZero();
    }

    @Test
    void releaseRestocksExactlyOnceAndIgnoresUnknownIds() {
        stock.reserve(request(R1, 13L, 2));
        assertThat(stock.release(R1)).isPresent();
        assertThat(stock.release(R1)).isPresent(); // a retry: no second restock
        assertThat(f.cleanCode.getStock()).isEqualTo(12);
        assertThat(f.reservationRows.get(R1).getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(stock.release(R2)).isEmpty(); // checkout died before reserving: nothing to undo
    }

    @Test
    void confirmIsIdempotentButAReleasedReservationCannotBeConfirmed() {
        stock.reserve(request(R1, 13L, 1));
        assertThat(stock.confirm(R1).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(stock.confirm(R1).status()).isEqualTo(ReservationStatus.CONFIRMED);

        stock.reserve(request(R2, 13L, 1));
        stock.release(R2);
        assertThatThrownBy(() -> stock.confirm(R2)).hasMessageContaining("already released");
        assertThatThrownBy(() -> stock.reserve(request(R2, 13L, 1))).hasMessageContaining("expired");
    }

    @Test
    void cancellingAConfirmedOrderPutsTheCopiesBack() {
        stock.reserve(request(R1, 20L, 2));
        stock.confirm(R1);
        stock.release(R1);
        assertThat(f.ikigai.getStock()).isEqualTo(2);
    }

    @Test
    void unconfirmedReservationsCountAsAbandonedAfterTheTimeout() {
        stock.reserve(request(R1, 13L, 1));
        stock.reserve(request(R2, 20L, 1));
        stock.confirm(R2);
        f.clock.advanceMinutes(29);
        assertThat(stock.abandoned(Duration.ofMinutes(30))).isEmpty();
        f.clock.advanceMinutes(2);
        assertThat(stock.abandoned(Duration.ofMinutes(30))).containsExactly(R1);

        new ReservationSweeper(stock, 30).releaseAbandoned();
        assertThat(f.cleanCode.getStock()).isEqualTo(12);
        assertThat(f.ikigai.getStock()).isEqualTo(1); // confirmed: still sold
    }
}
