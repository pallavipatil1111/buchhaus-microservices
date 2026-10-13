package com.buchhaus.order;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work in a local database transaction. Checkout calls the catalogue over HTTP between its
 * database steps, and a transaction must never stay open across a network call, so the steps are
 * separate short transactions instead of one {@code @Transactional} method.
 */
public interface LocalTx {

    <T> T run(Supplier<T> work);

    @Component
    class Spring implements LocalTx {

        private final TransactionTemplate template;

        public Spring(TransactionTemplate template) {
            this.template = template;
        }

        @Override
        public <T> T run(Supplier<T> work) {
            return template.execute(status -> work.get());
        }
    }
}
