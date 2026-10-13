package com.buchhaus.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Books and reservations in memory behind mocked repositories. */
final class CatalogFixture {

    final MutableClock clock = new MutableClock(Instant.parse("2026-10-14T06:00:00Z"));
    final BookRepository books = mock(BookRepository.class);
    final ReservationRepository reservations = mock(ReservationRepository.class);
    final Map<Long, Book> bookRows = new LinkedHashMap<>();
    final Map<String, Reservation> reservationRows = new LinkedHashMap<>();

    final Book cleanCode = book(13L, "Clean Code", 799_00, 12);
    final Book ikigai = book(20L, "Ikigai", 350_00, 2);
    final Book lastCopy = book(21L, "The Alchemist", 299_00, 1);

    CatalogFixture() {
        when(books.lockAllById(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return bookRows.values().stream().filter(b -> ids.contains(b.getId())).toList();
        });
        when(books.findById(any())).thenAnswer(inv -> Optional.ofNullable(bookRows.get((Long) inv.getArgument(0))));
        when(books.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservations.lockById(any())).thenAnswer(inv -> Optional.ofNullable(reservationRows.get((String) inv.getArgument(0))));
        when(reservations.save(any())).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            reservationRows.put(r.getId(), r);
            return r;
        });
        when(reservations.findIdsByStatusCreatedBefore(any(), any())).thenAnswer(inv -> {
            ReservationStatus status = inv.getArgument(0);
            Instant before = inv.getArgument(1);
            return reservationRows.values().stream()
                    .filter(r -> r.getStatus() == status && r.getCreatedAt().isBefore(before))
                    .map(Reservation::getId).toList();
        });
    }

    StockService stock() {
        return new StockService(books, reservations, clock);
    }

    Book book(Long id, String title, long paise, int stock) {
        Book b = new Book("BH-" + id, title, "Author", "Fiction", paise, stock);
        b.setId(id);
        bookRows.put(id, b);
        return b;
    }

    static Dtos.ReserveRequest request(String id, Object... bookIdQty) {
        List<Dtos.ReserveLine> lines = new java.util.ArrayList<>();
        for (int i = 0; i < bookIdQty.length; i += 2) {
            lines.add(new Dtos.ReserveLine((Long) bookIdQty[i], (Integer) bookIdQty[i + 1]));
        }
        return new Dtos.ReserveRequest(id, lines);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
