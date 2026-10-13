package com.buchhaus.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order life cycle: PLACED -> SHIPPED -> DELIVERED. An order can be cancelled until it has
 * shipped by the customer, and until it is delivered by an admin. Delivered and cancelled orders
 * are final.
 */
public enum OrderStatus {
    PLACED, SHIPPED, DELIVERED, CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PLACED -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED, CANCELLED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus target) {
        return next().contains(target);
    }

    /** Customers may only cancel before the parcel leaves the warehouse. */
    public boolean customerCanCancel() {
        return this == PLACED;
    }

    public boolean isFinal() {
        return next().isEmpty();
    }
}
