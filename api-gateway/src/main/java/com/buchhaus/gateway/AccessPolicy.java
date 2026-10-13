package com.buchhaus.gateway;

import com.buchhaus.common.AuthUser;
import java.util.Optional;

/**
 * Who may call what, decided once at the edge. Kept free of Spring types so it is a plain unit test.
 * <ul>
 *   <li>Public: sign-in and registration, browsing books and genres (GET), the web front end.</li>
 *   <li>Admin only: everything under /api/admin.</li>
 *   <li>Signed in: every other /api route (cart, orders, /api/me).</li>
 * </ul>
 */
public final class AccessPolicy {

    public enum Access {
        PUBLIC, SIGNED_IN, ADMIN
    }

    public enum Decision {
        ALLOW, UNAUTHORIZED, FORBIDDEN, BAD_REQUEST
    }

    private AccessPolicy() {
    }

    public static Access required(String method, String path) {
        if (!path.startsWith("/api/")) {
            return Access.PUBLIC;
        }
        if (path.startsWith("/api/auth/")) {
            return Access.PUBLIC;
        }
        if ("GET".equals(method) && (path.equals("/api/books") || path.startsWith("/api/books/") || path.equals("/api/genres"))) {
            return Access.PUBLIC;
        }
        if (path.equals("/api/admin") || path.startsWith("/api/admin/")) {
            return Access.ADMIN;
        }
        return Access.SIGNED_IN;
    }

    public static Decision decide(String method, String rawPath, Optional<AuthUser> caller) {
        if (!isSafe(rawPath)) {
            return Decision.BAD_REQUEST;
        }
        return switch (required(method, rawPath)) {
            case PUBLIC -> Decision.ALLOW;
            case SIGNED_IN -> caller.isPresent() ? Decision.ALLOW : Decision.UNAUTHORIZED;
            case ADMIN -> caller.isEmpty() ? Decision.UNAUTHORIZED
                    : caller.get().isAdmin() ? Decision.ALLOW : Decision.FORBIDDEN;
        };
    }

    /**
     * Rejects paths that could be read differently here and by the service behind (dot segments,
     * encoded characters, empty segments, matrix parameters), e.g. "/api/books/../admin/stats".
     */
    static boolean isSafe(String rawPath) {
        return rawPath != null && rawPath.startsWith("/") && !rawPath.contains("..") && !rawPath.contains("//")
                && !rawPath.contains("%") && !rawPath.contains(";") && !rawPath.contains("\\");
    }

    /** The token from an "Authorization: Bearer ..." header, if there is one. */
    public static Optional<String> bearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
