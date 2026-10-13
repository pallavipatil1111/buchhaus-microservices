package com.buchhaus.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.buchhaus.common.ApiException;
import com.buchhaus.common.JwtCodec;
import com.buchhaus.common.Role;
import com.buchhaus.user.Dtos.LoginRequest;
import com.buchhaus.user.Dtos.RegisterRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T06:00:00Z"), ZoneOffset.UTC);

    private UserRepository users;
    private PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new AuthService(users, encoder,
                new JwtCodec("test-secret-test-secret-test-secret-123", Duration.ofMinutes(60)), CLOCK);
    }

    @Test
    void duplicateEmailIsAConflictAfterNormalising() {
        when(users.existsByEmailIgnoreCase("reader@buchhaus.local")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("R", " Reader@BuchHaus.local ", "password1")))
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test
    void badCredentialsGiveOneGenericError() {
        User admin = new User("Admin", "admin@buchhaus.local", "hash", Role.ADMIN);
        when(users.findByEmailIgnoreCase("admin@buchhaus.local")).thenReturn(Optional.of(admin));
        when(encoder.matches("nope", "hash")).thenReturn(false);
        Throwable wrong = catchThrowable(() -> service.login(new LoginRequest("admin@buchhaus.local", "nope")));
        Throwable unknown = catchThrowable(() -> service.login(new LoginRequest("ghost@buchhaus.local", "nope")));
        assertThat(((ApiException) wrong).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getMessage()).isEqualTo(unknown.getMessage());
    }

    @Test
    void meForADeletedAccountIsUnauthorised() {
        when(users.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.me(9L)).hasMessage("Account no longer exists");
    }
}
