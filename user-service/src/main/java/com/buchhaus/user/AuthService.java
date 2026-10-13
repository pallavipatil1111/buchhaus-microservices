package com.buchhaus.user;

import com.buchhaus.common.ApiException;
import com.buchhaus.common.AuthUser;
import com.buchhaus.common.JwtCodec;
import com.buchhaus.common.Role;
import com.buchhaus.user.Dtos.AuthResponse;
import com.buchhaus.user.Dtos.LoginRequest;
import com.buchhaus.user.Dtos.RegisterRequest;
import com.buchhaus.user.Dtos.UserView;
import java.time.Clock;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts live only in this service; it is the only one that ever sees a password. */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtCodec jwt;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtCodec jwt, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password()),
                Role.CUSTOMER));
        return respond(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalise(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return respond(user);
    }

    @Transactional(readOnly = true)
    public UserView me(Long userId) {
        return UserView.of(users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists")));
    }

    private AuthResponse respond(User user) {
        String token = jwt.issue(new AuthUser(user.getId(), user.getEmail(), user.getRole()), clock.instant());
        return new AuthResponse(token, jwt.ttlSeconds(), UserView.of(user));
    }

    static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
