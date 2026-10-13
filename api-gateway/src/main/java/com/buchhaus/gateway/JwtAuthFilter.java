package com.buchhaus.gateway;

import com.buchhaus.common.AuthUser;
import com.buchhaus.common.CallerHeaders;
import com.buchhaus.common.JwtCodec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs before routing on every request:
 * <ol>
 *   <li>verifies the JWT (signature, issuer, expiry) and applies {@link AccessPolicy} (401 / 403);</li>
 *   <li>removes any identity headers the client tried to send, and the token itself;</li>
 *   <li>adds X-User-Id, X-User-Email and X-User-Role for the service behind.</li>
 * </ol>
 * The services never parse tokens; they trust these headers because only the gateway can reach them.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtCodec jwt;
    private final Clock clock;

    public JwtAuthFilter(JwtCodec jwt, Clock clock) {
        this.jwt = jwt;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();
        Optional<AuthUser> caller = AccessPolicy.bearer(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .flatMap(token -> jwt.verify(token, clock.instant()));
        switch (AccessPolicy.decide(request.getMethod().name(), path, caller)) {
            case BAD_REQUEST -> {
                return reject(exchange.getResponse(), HttpStatus.BAD_REQUEST, "Invalid request path");
            }
            case UNAUTHORIZED -> {
                return reject(exchange.getResponse(), HttpStatus.UNAUTHORIZED, "Please sign in");
            }
            case FORBIDDEN -> {
                return reject(exchange.getResponse(), HttpStatus.FORBIDDEN, "Admins only");
            }
            case ALLOW -> {
                // continue below
            }
        }
        ServerHttpRequest forwarded = request.mutate().headers(h -> {
            h.remove(CallerHeaders.USER_ID);
            h.remove(CallerHeaders.USER_EMAIL);
            h.remove(CallerHeaders.USER_ROLE);
            h.remove(HttpHeaders.AUTHORIZATION);
            caller.ifPresent(u -> {
                h.set(CallerHeaders.USER_ID, String.valueOf(u.id()));
                h.set(CallerHeaders.USER_EMAIL, u.email());
                h.set(CallerHeaders.USER_ROLE, u.role().name());
            });
        }).build();
        return chain.filter(exchange.mutate().request(forwarded).build());
    }

    /** Runs before Spring Cloud Gateway's routing filters. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private Mono<Void> reject(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        String body = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\""
                + message + "\",\"fields\":{},\"timestamp\":\"" + clock.instant() + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
