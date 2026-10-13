package com.buchhaus.order;

import com.buchhaus.common.CallerHeaders;
import com.buchhaus.order.Dtos.AdminStats;
import com.buchhaus.order.Dtos.CartView;
import com.buchhaus.order.Dtos.CheckoutRequest;
import com.buchhaus.order.Dtos.OrderView;
import com.buchhaus.order.Dtos.QuantityRequest;
import com.buchhaus.order.Dtos.StatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cart, orders and the admin order screens. The caller comes from the gateway's identity headers. */
@RestController
public class OrderController {

    private final CartService cart;
    private final OrderService orders;
    private final AdminService admin;

    public OrderController(CartService cart, OrderService orders, AdminService admin) {
        this.cart = cart;
        this.orders = orders;
        this.admin = admin;
    }

    @GetMapping("/api/cart")
    public CartView cart(@RequestHeader(CallerHeaders.USER_ID) Long userId) {
        return cart.view(userId);
    }

    @PutMapping("/api/cart/items/{bookId}")
    public CartView setQuantity(@RequestHeader(CallerHeaders.USER_ID) Long userId, @PathVariable Long bookId,
                                @Valid @RequestBody QuantityRequest request) {
        return cart.setQuantity(userId, bookId, request.quantity());
    }

    @DeleteMapping("/api/cart/items/{bookId}")
    public CartView remove(@RequestHeader(CallerHeaders.USER_ID) Long userId, @PathVariable Long bookId) {
        return cart.setQuantity(userId, bookId, 0);
    }

    @DeleteMapping("/api/cart")
    public CartView clear(@RequestHeader(CallerHeaders.USER_ID) Long userId) {
        return cart.clear(userId);
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView checkout(@RequestHeader(CallerHeaders.USER_ID) Long userId,
                              @RequestHeader(CallerHeaders.USER_EMAIL) String email,
                              @Valid @RequestBody CheckoutRequest request) {
        return orders.checkout(userId, email, request);
    }

    @GetMapping("/api/orders")
    public List<OrderView> myOrders(@RequestHeader(CallerHeaders.USER_ID) Long userId) {
        return orders.mine(userId);
    }

    @GetMapping("/api/orders/{id}")
    public OrderView order(@RequestHeader(CallerHeaders.USER_ID) Long userId, @PathVariable Long id) {
        return orders.get(userId, id);
    }

    @PostMapping("/api/orders/{id}/cancel")
    public OrderView cancel(@RequestHeader(CallerHeaders.USER_ID) Long userId, @PathVariable Long id) {
        return orders.cancel(userId, id);
    }

    @GetMapping("/api/admin/stats")
    public AdminStats stats(@RequestHeader(CallerHeaders.USER_ROLE) String role) {
        CallerHeaders.requireAdmin(role);
        return admin.stats();
    }

    @GetMapping("/api/admin/orders")
    public List<OrderView> allOrders(@RequestHeader(CallerHeaders.USER_ROLE) String role,
                                     @RequestParam(required = false) OrderStatus status) {
        CallerHeaders.requireAdmin(role);
        return orders.all(status);
    }

    @PatchMapping("/api/admin/orders/{id}/status")
    public OrderView updateStatus(@RequestHeader(CallerHeaders.USER_ROLE) String role, @PathVariable Long id,
                                  @Valid @RequestBody StatusRequest request) {
        CallerHeaders.requireAdmin(role);
        return orders.updateStatus(id, request.status());
    }
}
