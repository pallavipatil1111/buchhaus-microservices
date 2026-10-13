package com.buchhaus.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One book in a reservation, with the title and price at the moment it was reserved. */
@Entity
@Table(name = "reservation_lines")
public class ReservationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "title_snapshot", nullable = false, length = 160)
    private String titleSnapshot;

    @Column(name = "unit_price_paise", nullable = false)
    private long unitPricePaise;

    @Column(nullable = false)
    private int quantity;

    protected ReservationLine() {
    }

    ReservationLine(Reservation reservation, Long bookId, String titleSnapshot, long unitPricePaise, int quantity) {
        this.reservation = reservation;
        this.bookId = bookId;
        this.titleSnapshot = titleSnapshot;
        this.unitPricePaise = unitPricePaise;
        this.quantity = quantity;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getTitleSnapshot() {
        return titleSnapshot;
    }

    public long getUnitPricePaise() {
        return unitPricePaise;
    }

    public int getQuantity() {
        return quantity;
    }
}
