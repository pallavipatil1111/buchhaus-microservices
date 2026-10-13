package com.buchhaus.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingTest {

    @Test
    void freeShippingFromFourNinetyNine() {
        assertThat(Pricing.shipping(0)).isZero();
        assertThat(Pricing.shipping(498_99)).isEqualTo(49_00L);
        assertThat(Pricing.shipping(499_00)).isZero();
    }

    @Test
    void rupeesAndPaiseRoundTrip() {
        assertThat(Pricing.paise(new BigDecimal("799.5"))).isEqualTo(799_50L);
        assertThat(Pricing.rupees(799_50)).isEqualTo(new BigDecimal("799.50"));
        assertThatThrownBy(() -> Pricing.paise(new BigDecimal("1.005"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pricing.paise(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lineTotalsDoNotOverflowSilently() {
        assertThat(Pricing.lineTotal(1499_00, 3)).isEqualTo(4497_00L);
        assertThatThrownBy(() -> Pricing.lineTotal(Long.MAX_VALUE, 2)).isInstanceOf(ArithmeticException.class);
    }
}
