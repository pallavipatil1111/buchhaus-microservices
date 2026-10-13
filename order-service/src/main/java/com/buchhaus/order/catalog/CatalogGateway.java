package com.buchhaus.order.catalog;

import com.buchhaus.order.catalog.CatalogDtos.BookView;
import com.buchhaus.order.catalog.CatalogDtos.CatalogSummary;
import com.buchhaus.order.catalog.CatalogDtos.ReservationView;
import com.buchhaus.order.catalog.CatalogDtos.ReserveRequest;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The Feign client wrapped in Resilience4j. The retry sits outside the circuit breaker, and the
 * fallback (a friendly 503) runs only once retries are used up or the breaker is open:
 * <ul>
 *   <li><b>Retry</b> (3 attempts, exponential backoff) for timeouts and 5xx. Safe because every
 *       catalogue call is idempotent: reservations are keyed by an id chosen here.</li>
 *   <li><b>Circuit breaker</b>: after half of the last 10 calls fail, calls stop for 10 seconds and
 *       fail fast with a clear 503 instead of piling up threads waiting on a dead service.</li>
 *   <li>4xx answers (sold out, unknown book) are business results, not failures: they are neither
 *       retried nor counted by the breaker (see application.yml).</li>
 * </ul>
 */
@Component
public class CatalogGateway implements Catalog {

    private final CatalogClient client;

    public CatalogGateway(CatalogClient client) {
        this.client = client;
    }

    @Override
    @Retry(name = "catalog", fallbackMethod = "booksFallback")
    @CircuitBreaker(name = "catalog")
    public List<BookView> books(List<Long> ids) {
        return call(() -> client.books(ids));
    }

    @Override
    @Retry(name = "catalog", fallbackMethod = "reserveFallback")
    @CircuitBreaker(name = "catalog")
    public ReservationView reserve(ReserveRequest request) {
        return call(() -> client.reserve(request));
    }

    @Override
    @Retry(name = "catalog", fallbackMethod = "idFallback")
    @CircuitBreaker(name = "catalog")
    public void confirm(String reservationId) {
        call(() -> client.confirm(reservationId));
    }

    @Override
    @Retry(name = "catalog", fallbackMethod = "idFallback")
    @CircuitBreaker(name = "catalog")
    public void release(String reservationId) {
        call(() -> {
            client.release(reservationId);
            return null;
        });
    }

    @Override
    @CircuitBreaker(name = "catalog", fallbackMethod = "summaryFallback")
    public Optional<CatalogSummary> summary() {
        return Optional.ofNullable(call(client::summary));
    }

    List<BookView> booksFallback(List<Long> ids, Throwable t) {
        throw CatalogErrors.unavailable(t);
    }

    ReservationView reserveFallback(ReserveRequest request, Throwable t) {
        throw CatalogErrors.unavailable(t);
    }

    void idFallback(String reservationId, Throwable t) {
        throw CatalogErrors.unavailable(t);
    }

    Optional<CatalogSummary> summaryFallback(Throwable t) {
        if (t instanceof com.buchhaus.common.ApiException api) {
            throw api;
        }
        return Optional.empty();
    }

    static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                throw CatalogErrors.translate(e.status(), e.contentUTF8());
            }
            throw e; // connection refused, timeout or 5xx: let Retry and the breaker handle it
        }
    }
}
