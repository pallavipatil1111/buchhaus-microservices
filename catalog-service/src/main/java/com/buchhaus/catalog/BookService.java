package com.buchhaus.catalog;

import com.buchhaus.catalog.Dtos.BookRequest;
import com.buchhaus.catalog.Dtos.BookView;
import com.buchhaus.catalog.Dtos.PageView;
import com.buchhaus.common.ApiException;
import com.buchhaus.common.Pricing;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogue browsing for everyone, and title management for admins. */
@Service
public class BookService {

    public static final int MAX_PAGE_SIZE = 48;
    private static final Map<String, Sort> SORTS = Map.of(
            "relevance", Sort.by("title"),
            "title", Sort.by("title"),
            "price_asc", Sort.by(Sort.Direction.ASC, "pricePaise"),
            "price_desc", Sort.by(Sort.Direction.DESC, "pricePaise"),
            "newest", Sort.by(Sort.Direction.DESC, "publishedYear"));

    public static final int LOW_STOCK = 5;

    private final BookRepository books;
    private final ReservationRepository reservations;

    public BookService(BookRepository books, ReservationRepository reservations) {
        this.books = books;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public PageView<BookView> search(String q, String genre, String sort, int page, int size) {
        Sort order = SORTS.getOrDefault(sort == null ? "relevance" : sort, SORTS.get("relevance"));
        PageRequest request = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                order);
        return PageView.of(books.search(blankToNull(q), blankToNull(genre), request), BookView::of);
    }

    @Transactional(readOnly = true)
    public BookView get(Long id) {
        return BookView.of(books.findByIdAndActiveTrue(id).orElseThrow(() -> ApiException.notFound("Book")));
    }

    @Transactional(readOnly = true)
    public List<String> genres() {
        return books.findGenres();
    }

    // ---------- admin ----------

    @Transactional
    public BookView create(BookRequest r) {
        if (books.existsBySkuIgnoreCase(r.sku())) {
            throw ApiException.conflict("SKU " + r.sku() + " is already used");
        }
        Book b = new Book(r.sku(), r.title().trim(), r.author().trim(), r.genre().trim(), toPaise(r), r.stock());
        apply(b, r);
        return BookView.of(books.save(b));
    }

    @Transactional
    public BookView update(Long id, BookRequest r) {
        Book b = books.findById(id).orElseThrow(() -> ApiException.notFound("Book"));
        if (r.version() != null && r.version() != b.getVersion()) {
            throw ApiException.conflict("Someone else changed this book - reload and try again");
        }
        if (!b.getSku().equalsIgnoreCase(r.sku()) && books.existsBySkuIgnoreCase(r.sku())) {
            throw ApiException.conflict("SKU " + r.sku() + " is already used");
        }
        b.setSku(r.sku());
        b.setTitle(r.title().trim());
        b.setAuthor(r.author().trim());
        b.setGenre(r.genre().trim());
        b.setPricePaise(toPaise(r));
        b.setStock(r.stock());
        apply(b, r);
        b.setActive(true);
        return BookView.of(books.saveAndFlush(b));   // flush so the response carries the new @Version
    }

    /**
     * Books that have been sold are retired (hidden from the shop, kept for order history); books never
     * sold are deleted outright. The catalogue knows what was sold from its own reservations, so it
     * does not need to ask the order-service.
     */
    @Transactional
    public boolean remove(Long id) {
        Book b = books.findById(id).orElseThrow(() -> ApiException.notFound("Book"));
        if (reservations.wasSold(id, ReservationStatus.RELEASED)) {
            b.setActive(false);
            books.save(b);
            return false;
        }
        books.delete(b);
        return true;
    }

    @Transactional(readOnly = true)
    public List<BookView> all() {
        return books.findAll(Sort.by("title")).stream().map(BookView::of).toList();
    }

    /** Books by id for the order-service's cart, including retired ones so it can flag them. */
    @Transactional(readOnly = true)
    public List<BookView> byIds(List<Long> ids) {
        return books.findByIdIn(ids).stream().map(BookView::of).toList();
    }

    @Transactional(readOnly = true)
    public Dtos.CatalogSummary summary() {
        return new Dtos.CatalogSummary(books.countByActiveTrue(),
                books.findTop5ByActiveTrueAndStockLessThanOrderByStockAsc(LOW_STOCK).stream().map(BookView::of).toList());
    }

    private static void apply(Book b, BookRequest r) {
        b.setDescription(blankToNull(r.description()));
        b.setPublishedYear(r.publishedYear());
        b.setPages(r.pages());
    }

    private static long toPaise(BookRequest r) {
        try {
            return Pricing.paise(r.price());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
