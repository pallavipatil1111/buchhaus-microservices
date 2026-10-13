package com.buchhaus.order;

import com.buchhaus.common.ApiException;
import com.buchhaus.order.catalog.Catalog;
import com.buchhaus.order.catalog.CatalogDtos.BookView;
import com.buchhaus.order.catalog.CatalogDtos.CatalogSummary;
import com.buchhaus.order.catalog.CatalogDtos.ReservationView;
import com.buchhaus.order.catalog.CatalogDtos.ReserveLine;
import com.buchhaus.order.catalog.CatalogDtos.ReserveRequest;
import com.buchhaus.order.catalog.CatalogDtos.ReservedLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A catalogue in memory that behaves like the real one, and can be switched "down". */
final class FakeCatalog implements Catalog {

    final Map<Long, int[]> stock = new LinkedHashMap<>();       // bookId -> {stock}
    final Map<Long, BookView> books = new LinkedHashMap<>();
    final Map<String, String> reservations = new LinkedHashMap<>(); // id -> status
    final Map<String, List<ReserveLine>> reservedLines = new LinkedHashMap<>();
    final List<String> calls = new ArrayList<>();
    boolean down;
    boolean confirmDown;

    void book(long id, String title, String price, int copies) {
        books.put(id, new BookView(id, "BH-" + id, title, "Author", "Fiction", null, new BigDecimal(price), copies,
                2001, 200, true, 0));
        stock.put(id, new int[] {copies});
    }

    private void check(String call) {
        calls.add(call);
        if (down) {
            throw ApiException.unavailable("The catalogue is temporarily unavailable. Your cart is safe - please try again in a minute.");
        }
    }

    @Override
    public List<BookView> books(List<Long> ids) {
        check("books");
        return ids.stream().filter(books::containsKey).map(id -> {
            BookView b = books.get(id);
            return new BookView(b.id(), b.sku(), b.title(), b.author(), b.genre(), b.description(), b.price(),
                    stock.get(id)[0], b.publishedYear(), b.pages(), b.active(), b.version());
        }).toList();
    }

    @Override
    public ReservationView reserve(ReserveRequest request) {
        check("reserve");
        for (ReserveLine l : request.lines()) {
            if (stock.get(l.bookId())[0] < l.quantity()) {
                throw ApiException.conflict("\"" + books.get(l.bookId()).title() + "\" just sold out");
            }
        }
        List<ReservedLine> lines = new ArrayList<>();
        for (ReserveLine l : request.lines()) {
            stock.get(l.bookId())[0] -= l.quantity();
            BookView b = books.get(l.bookId());
            lines.add(new ReservedLine(b.id(), b.title(), b.price().movePointRight(2).longValueExact(), l.quantity()));
        }
        reservations.put(request.reservationId(), "RESERVED");
        reservedLines.put(request.reservationId(), request.lines());
        return new ReservationView(request.reservationId(), "RESERVED", lines);
    }

    @Override
    public void confirm(String reservationId) {
        check("confirm");
        if (confirmDown) {
            throw ApiException.unavailable("down");
        }
        reservations.put(reservationId, "CONFIRMED");
    }

    @Override
    public void release(String reservationId) {
        check("release");
        if (reservations.containsKey(reservationId) && !"RELEASED".equals(reservations.get(reservationId))) {
            for (ReserveLine l : reservedLines.get(reservationId)) {
                stock.get(l.bookId())[0] += l.quantity();
            }
            reservations.put(reservationId, "RELEASED");
        }
    }

    @Override
    public Optional<CatalogSummary> summary() {
        calls.add("summary");
        return down ? Optional.empty() : Optional.of(new CatalogSummary(books.size(), List.of()));
    }
}
