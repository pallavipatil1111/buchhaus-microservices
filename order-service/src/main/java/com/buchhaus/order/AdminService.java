package com.buchhaus.order;

import com.buchhaus.common.Pricing;
import com.buchhaus.order.Dtos.AdminStats;
import com.buchhaus.order.catalog.Catalog;
import com.buchhaus.order.catalog.CatalogDtos.CatalogSummary;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Admin dashboard: still loads when the catalogue is down, just without its stock figures. */
@Service
public class AdminService {

    private final OrderRepository orders;
    private final Catalog catalog;
    private final LocalTx tx;

    public AdminService(OrderRepository orders, Catalog catalog, LocalTx tx) {
        this.orders = orders;
        this.catalog = catalog;
        this.tx = tx;
    }

    public AdminStats stats() {
        Optional<CatalogSummary> summary = catalog.summary();
        return tx.run(() -> {
            Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
            long total = 0;
            for (OrderStatus s : OrderStatus.values()) {
                long n = orders.countByStatus(s);
                byStatus.put(s, n);
                total += n;
            }
            return new AdminStats(Pricing.rupees(orders.revenueExcluding(OrderStatus.CANCELLED)), total, byStatus,
                    summary.map(CatalogSummary::activeTitles).orElse(null),
                    summary.map(CatalogSummary::lowStock).orElse(List.of()),
                    orders.topSellers(OrderStatus.CANCELLED, PageRequest.of(0, 5)), summary.isPresent());
        });
    }
}
