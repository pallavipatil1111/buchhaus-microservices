package com.buchhaus.common;

/**
 * Identity headers the API gateway adds after it has verified the JWT. The gateway always removes
 * any copies a client sends, and the services are not reachable from outside the Docker network,
 * so a service can trust these headers.
 */
public final class CallerHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLE = "X-User-Role";

    private CallerHeaders() {
    }

    /** Defence in depth: the gateway already blocks non-admins, but admin endpoints check again. */
    public static void requireAdmin(String role) {
        if (!Role.ADMIN.name().equals(role)) {
            throw ApiException.forbidden("Admins only");
        }
    }
}
