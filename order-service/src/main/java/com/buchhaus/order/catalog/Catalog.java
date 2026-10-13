package com.buchhaus.order.catalog;

import com.buchhaus.order.catalog.CatalogDtos.BookView;
import com.buchhaus.order.catalog.CatalogDtos.CatalogSummary;
import com.buchhaus.order.catalog.CatalogDtos.ReservationView;
import com.buchhaus.order.catalog.CatalogDtos.ReserveRequest;
import java.util.List;
import java.util.Optional;

/**
 * What the order-service needs from the catalogue. Business rules answer with
 * {@link com.buchhaus.common.ApiException} (sold out: 409); when the catalogue cannot be reached the
 * calls fail with a 503 ApiException instead of hanging.
 */
public interface Catalog {

    List<BookView> books(List<Long> ids);

    ReservationView reserve(ReserveRequest request);

    void confirm(String reservationId);

    void release(String reservationId);

    /** Empty when the catalogue is down: the admin dashboard still loads, without stock figures. */
    Optional<CatalogSummary> summary();
}
