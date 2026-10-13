package com.buchhaus.catalog;

/**
 * RESERVED: copies taken out of stock for a checkout in progress.
 * CONFIRMED: the order-service saved the order; the copies are sold.
 * RELEASED: the copies went back on the shelf (checkout failed, order cancelled, or the reservation
 * expired because no one confirmed it).
 */
public enum ReservationStatus {
    RESERVED, CONFIRMED, RELEASED
}
