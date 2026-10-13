package com.buchhaus.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An order. Line items snapshot title and price; {@code reservationId} links it to the stock the
 * catalogue holds for it, and {@code stockConfirmed} records whether the catalogue has been told the
 * sale went through (if not, {@link ConfirmRetrier} keeps trying).
 */
@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "customer_email", nullable = false, length = 160)
    private String customerEmail;

    @Column(name = "reservation_id", nullable = false, length = 36, unique = true)
    private String reservationId;

    @Column(name = "stock_confirmed", nullable = false)
    private boolean stockConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(name = "subtotal_paise", nullable = false)
    private long subtotalPaise;

    @Column(name = "shipping_paise", nullable = false)
    private long shippingPaise;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    @Column(name = "ship_name", nullable = false, length = 80)
    private String shipName;

    @Column(name = "ship_phone", nullable = false, length = 15)
    private String shipPhone;

    @Column(name = "ship_address", nullable = false, length = 200)
    private String shipAddress;

    @Column(name = "ship_city", nullable = false, length = 60)
    private String shipCity;

    @Column(name = "ship_pincode", nullable = false, length = 6)
    private String shipPincode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(Long userId, String customerEmail, String reservationId, String shipName, String shipPhone, String shipAddress, String shipCity,
                         String shipPincode) {
        this.userId = userId;
        this.customerEmail = customerEmail;
        this.reservationId = reservationId;
        this.shipName = shipName;
        this.shipPhone = shipPhone;
        this.shipAddress = shipAddress;
        this.shipCity = shipCity;
        this.shipPincode = shipPincode;
    }

    public void addItem(Long bookId, String title, long unitPricePaise, int quantity) {
        items.add(new OrderItem(this, bookId, title, unitPricePaise, quantity));
    }

    public void markStockConfirmed() {
        this.stockConfirmed = true;
    }

    public void setTotals(long subtotal, long shipping) {
        this.subtotalPaise = subtotal;
        this.shippingPaise = shipping;
        this.totalPaise = subtotal + shipping;
    }

    public void moveTo(OrderStatus target, Instant when) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Cannot move an order from " + status + " to " + target);
        }
        status = target;
        updatedAt = when;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getReservationId() {
        return reservationId;
    }

    public boolean isStockConfirmed() {
        return stockConfirmed;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getSubtotalPaise() {
        return subtotalPaise;
    }

    public long getShippingPaise() {
        return shippingPaise;
    }

    public long getTotalPaise() {
        return totalPaise;
    }

    public String getShipName() {
        return shipName;
    }

    public String getShipPhone() {
        return shipPhone;
    }

    public String getShipAddress() {
        return shipAddress;
    }

    public String getShipCity() {
        return shipCity;
    }

    public String getShipPincode() {
        return shipPincode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
