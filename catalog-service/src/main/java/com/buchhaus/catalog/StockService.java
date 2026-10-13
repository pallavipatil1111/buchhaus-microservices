package com.buchhaus.catalog;

import com.buchhaus.catalog.Dtos.ReservationView;
import com.buchhaus.catalog.Dtos.ReserveLine;
import com.buchhaus.catalog.Dtos.ReserveRequest;
import com.buchhaus.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The catalogue's half of checkout (a saga with the order-service):
 * <ol>
 *   <li>{@link #reserve}: lock the books, check stock, take the copies, remember the prices.</li>
 *   <li>{@link #confirm}: the order was saved; the copies are sold.</li>
 *   <li>{@link #release}: the checkout failed or the order was cancelled; copies go back.</li>
 * </ol>
 * Every step is idempotent (keyed by the reservation id), so the order-service can safely retry a
 * call whose answer was lost.
 */
@Service
public class StockService {

    private final BookRepository books;
    private final ReservationRepository reservations;
    private final Clock clock;

    public StockService(BookRepository books, ReservationRepository reservations, Clock clock) {
        this.books = books;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Transactional
    public ReservationView reserve(ReserveRequest request) {
        Optional<Reservation> existing = reservations.lockById(request.reservationId());
        if (existing.isPresent()) {
            // A retry of a request that already succeeded: return the same answer, take nothing twice.
            if (existing.get().getStatus() == ReservationStatus.RELEASED) {
                throw ApiException.conflict("This checkout expired - please place the order again");
            }
            return ReservationView.of(existing.get());
        }
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (ReserveLine line : request.lines()) {
            wanted.merge(line.bookId(), line.quantity(), Integer::sum);
        }
        Map<Long, Book> locked = books.lockAllById(wanted.keySet()).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));
        Reservation reservation = new Reservation(request.reservationId(), clock.instant());
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Book book = locked.get(e.getKey());
            if (book == null || !book.isActive()) {
                throw ApiException.conflict("A book in your cart is no longer sold - remove it and try again");
            }
            if (book.getStock() < e.getValue()) {
                throw ApiException.conflict(book.getStock() == 0
                        ? "\"" + book.getTitle() + "\" just sold out"
                        : "Only " + book.getStock() + " copies of \"" + book.getTitle() + "\" left");
            }
            book.removeStock(e.getValue());
            reservation.add(book, e.getValue());
        }
        return ReservationView.of(reservations.save(reservation));
    }

    @Transactional
    public ReservationView confirm(String reservationId) {
        Reservation r = reservations.lockById(reservationId)
                .orElseThrow(() -> ApiException.notFound("Reservation"));
        switch (r.getStatus()) {
            case RESERVED -> r.moveTo(ReservationStatus.CONFIRMED, clock.instant());
            case CONFIRMED -> {
                // already confirmed: nothing to do
            }
            case RELEASED -> throw ApiException.conflict("Reservation " + reservationId + " was already released");
        }
        return ReservationView.of(r);
    }

    /** Puts the copies back. Releasing twice, or releasing an id that was never reserved, does nothing. */
    @Transactional
    public Optional<ReservationView> release(String reservationId) {
        Optional<Reservation> found = reservations.lockById(reservationId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Reservation r = found.get();
        if (r.getStatus() != ReservationStatus.RELEASED) {
            List<Long> ids = r.getLines().stream().map(ReservationLine::getBookId).toList();
            Map<Long, Book> locked = books.lockAllById(ids).stream()
                    .collect(Collectors.toMap(Book::getId, Function.identity()));
            for (ReservationLine line : r.getLines()) {
                Book book = locked.get(line.getBookId());
                if (book != null) {
                    book.restock(line.getQuantity());
                }
            }
            r.moveTo(ReservationStatus.RELEASED, clock.instant());
        }
        return Optional.of(ReservationView.of(r));
    }

    /** Ids of reservations nobody confirmed within {@code timeout}: the checkout that made them died. */
    @Transactional(readOnly = true)
    public List<String> abandoned(Duration timeout) {
        return reservations.findIdsByStatusCreatedBefore(ReservationStatus.RESERVED, clock.instant().minus(timeout));
    }
}
