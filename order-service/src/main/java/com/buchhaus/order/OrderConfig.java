package com.buchhaus.order;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Reservation ids are chosen here, before calling the catalogue, so retries are idempotent. */
    @Bean
    public Supplier<String> reservationIds() {
        return () -> UUID.randomUUID().toString();
    }
}
