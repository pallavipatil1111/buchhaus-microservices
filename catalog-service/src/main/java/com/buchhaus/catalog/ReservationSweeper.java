package com.buchhaus.catalog;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * If the order-service crashed between reserving stock and confirming it, those copies would be
 * stuck forever. This job releases reservations that stayed unconfirmed for too long. Its first run
 * waits five minutes after start-up, giving the order-service time to re-confirm after an outage.
 */
@Component
public class ReservationSweeper {

    private static final Logger log = LoggerFactory.getLogger(ReservationSweeper.class);

    private final StockService stock;
    private final Duration timeout;

    public ReservationSweeper(StockService stock, @Value("${app.reservation-timeout-minutes:30}") long minutes) {
        this.stock = stock;
        this.timeout = Duration.ofMinutes(minutes);
    }

    @Scheduled(initialDelay = 300_000, fixedDelay = 60_000)
    public void releaseAbandoned() {
        for (String id : stock.abandoned(timeout)) {
            stock.release(id);
            log.warn("Released reservation {}: not confirmed within {}", id, timeout);
        }
    }
}
