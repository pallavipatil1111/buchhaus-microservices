package com.buchhaus.user;

import com.buchhaus.common.CallerHeaders;
import com.buchhaus.user.Dtos.AuthResponse;
import com.buchhaus.user.Dtos.LoginRequest;
import com.buchhaus.user.Dtos.RegisterRequest;
import com.buchhaus.user.Dtos.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /** The gateway has verified the token and passes the caller's id in a header. */
    @GetMapping("/api/me")
    public UserView me(@RequestHeader(CallerHeaders.USER_ID) Long userId) {
        return auth.me(userId);
    }
}
