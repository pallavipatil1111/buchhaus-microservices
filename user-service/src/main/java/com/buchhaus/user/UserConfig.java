package com.buchhaus.user;

import com.buchhaus.common.JwtCodec;
import com.buchhaus.common.Role;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserConfig {

    public static final String DEMO_EMAIL = "reader@buchhaus.local";
    public static final String DEMO_PASSWORD = "reader1234";

    private static final Logger log = LoggerFactory.getLogger(UserConfig.class);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtCodec jwtCodec(@Value("${app.jwt.secret}") String secret,
                             @Value("${app.jwt.expiry-minutes:1440}") long minutes) {
        return new JwtCodec(secret, Duration.ofMinutes(minutes));
    }

    /** Creates the admin (ADMIN_EMAIL / ADMIN_PASSWORD) and, with DEMO_DATA=true, a demo customer. */
    @Bean
    public CommandLineRunner seedAccounts(UserRepository users, PasswordEncoder encoder,
                                          @Value("${app.admin.email}") String adminEmail,
                                          @Value("${app.admin.password}") String adminPassword,
                                          @Value("${app.demo-data:false}") boolean demoData) {
        return args -> {
            if (!users.existsByEmailIgnoreCase(adminEmail)) {
                users.save(new User("Store admin", AuthService.normalise(adminEmail), encoder.encode(adminPassword), Role.ADMIN));
                log.info("Created admin account {}", adminEmail);
            }
            if (demoData && !users.existsByEmailIgnoreCase(DEMO_EMAIL)) {
                users.save(new User("Demo Reader", DEMO_EMAIL, encoder.encode(DEMO_PASSWORD), Role.CUSTOMER));
                log.info("Demo customer ready: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
            }
        };
    }
}
