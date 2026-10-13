package com.buchhaus.order;

import com.buchhaus.common.ApiException;
import com.buchhaus.common.Pricing;
import com.buchhaus.order.Dtos.CartLine;
import com.buchhaus.order.Dtos.CartView;
import com.buchhaus.order.catalog.Catalog;
import com.buchhaus.order.catalog.CatalogDtos.BookView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The cart is stored here (book ids and quantities); titles, prices and stock always come fresh
 * from the catalogue, so the cart never shows a stale price.
 */
@Service
public class CartService {

    private final CartItemRepository cartItems;
    private final Catalog catalog;
    private final LocalTx tx;

    public CartService(CartItemRepository cartItems, Catalog catalog, LocalTx tx) {
        this.cartItems = cartItems;
        this.catalog = catalog;
        this.tx = tx;
    }

    public CartView view(Long userId) {
        List<CartItem> items = tx.run(() -> cartItems.findByUserIdOrderByIdAsc(userId));
        if (items.isEmpty()) {
            return summarise(items, Map.of());
        }
        Map<Long, BookView> books = catalog.books(items.stream().map(CartItem::getBookId).toList()).stream()
                .collect(Collectors.toMap(BookView::id, Function.identity()));
        List<CartItem> gone = items.stream().filter(i -> !books.containsKey(i.getBookId())).toList();
        if (!gone.isEmpty()) {
            // The book was deleted from the catalogue: drop it from the cart.
            tx.run(() -> {
                cartItems.deleteAll(gone);
                return null;
            });
        }
        return summarise(items.stream().filter(i -> books.containsKey(i.getBookId())).toList(), books);
    }

    /** Sets the quantity of a book in the cart; 0 removes it. Quantity can't exceed stock. */
    public CartView setQuantity(Long userId, Long bookId, int quantity) {
        if (quantity < 0 || quantity > Pricing.MAX_QUANTITY_PER_BOOK) {
            throw ApiException.badRequest("Quantity must be between 0 and " + Pricing.MAX_QUANTITY_PER_BOOK);
        }
        if (quantity > 0) {
            BookView book = catalog.books(List.of(bookId)).stream().filter(BookView::active).findFirst()
                    .orElseThrow(() -> ApiException.notFound("Book"));
            if (quantity > book.stock()) {
                throw ApiException.conflict(book.stock() == 0 ? "\"" + book.title() + "\" is out of stock"
                        : "Only " + book.stock() + " copies of \"" + book.title() + "\" left");
            }
        }
        tx.run(() -> {
            var existing = cartItems.findByUserIdAndBookId(userId, bookId);
            if (quantity == 0) {
                existing.ifPresent(cartItems::delete);
            } else if (existing.isPresent()) {
                existing.get().setQuantity(quantity);
                cartItems.save(existing.get());
            } else {
                cartItems.save(new CartItem(userId, bookId, quantity));
            }
            return null;
        });
        return view(userId);
    }

    public CartView clear(Long userId) {
        tx.run(() -> {
            cartItems.deleteAllForUser(userId);
            return null;
        });
        return view(userId);
    }

    static CartView summarise(List<CartItem> items, Map<Long, BookView> books) {
        long subtotal = 0;
        int count = 0;
        boolean allAvailable = !items.isEmpty();
        List<CartLine> lines = new ArrayList<>();
        for (CartItem item : items) {
            BookView b = books.get(item.getBookId());
            boolean available = b.active() && b.stock() >= item.getQuantity();
            allAvailable &= available;
            long line = Pricing.lineTotal(Pricing.paise(b.price()), item.getQuantity());
            subtotal += line;
            count += item.getQuantity();
            lines.add(new CartLine(b, item.getQuantity(), Pricing.rupees(line), available));
        }
        long shipping = Pricing.shipping(subtotal);
        long gap = subtotal == 0 ? Pricing.FREE_SHIPPING_FROM : Math.max(0, Pricing.FREE_SHIPPING_FROM - subtotal);
        return new CartView(lines, count, Pricing.rupees(subtotal), Pricing.rupees(shipping),
                Pricing.rupees(subtotal + shipping), Pricing.rupees(gap), allAvailable);
    }
}
