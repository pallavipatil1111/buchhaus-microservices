package com.buchhaus.gateway;

import com.buchhaus.common.JwtCodec;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Same secret as the user-service, which issues the tokens. */
    @Bean
    public JwtCodec jwtCodec(@Value("${app.jwt.secret}") String secret,
                             @Value("${app.jwt.expiry-minutes:1440}") long minutes) {
        return new JwtCodec(secret, Duration.ofMinutes(minutes));
    }
}
