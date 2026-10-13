package com.buchhaus.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.buchhaus.common.AuthUser;
import com.buchhaus.common.Role;
import com.buchhaus.gateway.AccessPolicy.Decision;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccessPolicyTest {

    private static final Optional<AuthUser> NOBODY = Optional.empty();
    private static final Optional<AuthUser> CUSTOMER = Optional.of(new AuthUser(2L, "reader@buchhaus.local", Role.CUSTOMER));
    private static final Optional<AuthUser> ADMIN = Optional.of(new AuthUser(1L, "admin@buchhaus.local", Role.ADMIN));

    @Test
    void browsingAndSigningInArePublic() {
        assertThat(AccessPolicy.decide("GET", "/api/books", NOBODY)).isEqualTo(Decision.ALLOW);
        assertThat(AccessPolicy.decide("GET", "/api/books/7", NOBODY)).isEqualTo(Decision.ALLOW);
        assertThat(AccessPolicy.decide("GET", "/api/genres", NOBODY)).isEqualTo(Decision.ALLOW);
        assertThat(AccessPolicy.decide("POST", "/api/auth/login", NOBODY)).isEqualTo(Decision.ALLOW);
        assertThat(AccessPolicy.decide("POST", "/api/auth/register", NOBODY)).isEqualTo(Decision.ALLOW);
        assertThat(AccessPolicy.decide("GET", "/", NOBODY)).isEqualTo(Decision.ALLOW);
        assertThat(AccessPolicy.decide("GET", "/js/app.js", NOBODY)).isEqualTo(Decision.ALLOW);
    }

    @Test
    void cartOrdersAndProfileNeedASignedInUser() {
        for (String path : new String[] {"/api/cart", "/api/cart/items/3", "/api/orders", "/api/orders/5/cancel", "/api/me"}) {
            assertThat(AccessPolicy.decide("GET", path, NOBODY)).isEqualTo(Decision.UNAUTHORIZED);
            assertThat(AccessPolicy.decide("GET", path, CUSTOMER)).isEqualTo(Decision.ALLOW);
        }
        // writing to the catalogue is never public, even under /api/books
        assertThat(AccessPolicy.decide("POST", "/api/books", NOBODY)).isEqualTo(Decision.UNAUTHORIZED);
    }

    @Test
    void adminRoutesNeedTheAdminRole() {
        assertThat(AccessPolicy.decide("GET", "/api/admin/stats", NOBODY)).isEqualTo(Decision.UNAUTHORIZED);
        assertThat(AccessPolicy.decide("GET", "/api/admin/stats", CUSTOMER)).isEqualTo(Decision.FORBIDDEN);
        assertThat(AccessPolicy.decide("PATCH", "/api/admin/orders/4/status", ADMIN)).isEqualTo(Decision.ALLOW);
        assertThat(AccessPolicy.decide("DELETE", "/api/admin/books/2", CUSTOMER)).isEqualTo(Decision.FORBIDDEN);
    }

    @Test
    void ambiguousPathsAreRefusedBeforeRouting() {
        assertThat(AccessPolicy.decide("GET", "/api/books/../admin/stats", CUSTOMER)).isEqualTo(Decision.BAD_REQUEST);
        assertThat(AccessPolicy.decide("GET", "/api/books/%2e%2e/admin/stats", CUSTOMER)).isEqualTo(Decision.BAD_REQUEST);
        assertThat(AccessPolicy.decide("GET", "//api/admin/stats", CUSTOMER)).isEqualTo(Decision.BAD_REQUEST);
        assertThat(AccessPolicy.decide("GET", "/api/books;x=1", NOBODY)).isEqualTo(Decision.BAD_REQUEST);
    }

    @Test
    void readsBearerTokens() {
        assertThat(AccessPolicy.bearer("Bearer abc.def")).contains("abc.def");
        assertThat(AccessPolicy.bearer("Bearer ")).isEmpty();
        assertThat(AccessPolicy.bearer("Basic xyz")).isEmpty();
        assertThat(AccessPolicy.bearer(null)).isEmpty();
    }
}
