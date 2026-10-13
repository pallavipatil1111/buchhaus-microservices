package com.buchhaus.common;

/** The signed-in caller, as carried in the JWT and forwarded by the gateway. */
public record AuthUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
