package com.buchhaus.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A sold book: title and price are copied from the catalogue's reservation, so history never changes. */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "title_snapshot", nullable = false, length = 160)
    private String titleSnapshot;

    @Column(name = "unit_price_paise", nullable = false)
    private long unitPricePaise;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    OrderItem(CustomerOrder order, Long bookId, String titleSnapshot, long unitPricePaise, int quantity) {
        this.order = order;
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

    public long getLineTotalPaise() {
        return unitPricePaise * quantity;
    }
}
