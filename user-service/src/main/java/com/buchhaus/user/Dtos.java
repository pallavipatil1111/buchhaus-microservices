package com.buchhaus.user;

import com.buchhaus.common.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies, unchanged from the monolith so the web front end works as before. */
public final class Dtos {

    private Dtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserView(Long id, String name, String email, Role role) {

        public static UserView of(User u) {
            return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole());
        }
    }

    public record AuthResponse(String token, long expiresIn, UserView user) {
    }
}
