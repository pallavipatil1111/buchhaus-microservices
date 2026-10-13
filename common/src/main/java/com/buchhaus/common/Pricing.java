package com.buchhaus.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money is kept in whole paise ({@code long}) end to end; rupees only appear at the API edge.
 * Shipping is free from ₹499, otherwise a flat ₹49.
 */
public final class Pricing {

    public static final long FREE_SHIPPING_FROM = 499_00;
    public static final long SHIPPING_FEE = 49_00;
    public static final int MAX_QUANTITY_PER_BOOK = 10;

    private Pricing() {
    }

    public static long shipping(long subtotalPaise) {
        if (subtotalPaise <= 0) {
            return 0;
        }
        return subtotalPaise >= FREE_SHIPPING_FROM ? 0 : SHIPPING_FEE;
    }

    public static long lineTotal(long unitPaise, int quantity) {
        return Math.multiplyExact(unitPaise, quantity);
    }

    public static BigDecimal rupees(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Parses a rupee amount (max two decimals) into paise; rejects negatives. */
    public static long paise(BigDecimal rupees) {
        if (rupees == null || rupees.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or more");
        }
        BigDecimal p = rupees.movePointRight(2);
        if (p.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Price can have at most two decimals");
        }
        return p.longValueExact();
    }
}
