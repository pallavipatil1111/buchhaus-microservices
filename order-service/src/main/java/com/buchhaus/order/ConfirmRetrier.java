package com.buchhaus.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-confirms stock reservations for orders saved while the catalogue was unreachable. */
@Component
public class ConfirmRetrier {

    private static final Logger log = LoggerFactory.getLogger(ConfirmRetrier.class);

    private final OrderService orders;

    public ConfirmRetrier(OrderService orders) {
        this.orders = orders;
    }

    @Scheduled(initialDelay = 30_000, fixedDelayString = "${app.confirm-retry-ms:60000}")
    public void run() {
        int confirmed = orders.confirmPending();
        if (confirmed > 0) {
            log.info("Confirmed {} reservation(s) after a catalogue outage", confirmed);
        }
    }
}
