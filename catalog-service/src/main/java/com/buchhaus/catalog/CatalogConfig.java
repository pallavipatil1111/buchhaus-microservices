package com.buchhaus.catalog;

import com.buchhaus.common.web.ApiExceptionHandler;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Configuration
public class CatalogConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Two admins saved the same book at once (JPA @Version). */
    @RestControllerAdvice
    public static class ConcurrencyErrors {

        @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
        public ResponseEntity<ApiExceptionHandler.ErrorBody> handle(ObjectOptimisticLockingFailureException e) {
            return ApiExceptionHandler.body(HttpStatus.CONFLICT,
                    "Someone else changed this at the same time - reload and try again", Map.of());
        }
    }
}
