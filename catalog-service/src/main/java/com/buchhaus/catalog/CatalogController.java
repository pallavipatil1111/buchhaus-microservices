package com.buchhaus.catalog;

import com.buchhaus.catalog.Dtos.BookRequest;
import com.buchhaus.catalog.Dtos.BookView;
import com.buchhaus.catalog.Dtos.PageView;
import com.buchhaus.common.CallerHeaders;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public catalogue and admin title management (same paths as the monolith). */
@RestController
public class CatalogController {

    private final BookService books;

    public CatalogController(BookService books) {
        this.books = books;
    }

    @GetMapping("/api/books")
    public PageView<BookView> search(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) String genre,
                                     @RequestParam(defaultValue = "relevance") String sort,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "12") int size) {
        return books.search(q, genre, sort, page, size);
    }

    @GetMapping("/api/books/{id}")
    public BookView book(@PathVariable Long id) {
        return books.get(id);
    }

    @GetMapping("/api/genres")
    public List<String> genres() {
        return books.genres();
    }

    @GetMapping("/api/admin/books")
    public List<BookView> all(@RequestHeader(CallerHeaders.USER_ROLE) String role) {
        CallerHeaders.requireAdmin(role);
        return books.all();
    }

    @PostMapping("/api/admin/books")
    @ResponseStatus(HttpStatus.CREATED)
    public BookView create(@RequestHeader(CallerHeaders.USER_ROLE) String role, @Valid @RequestBody BookRequest request) {
        CallerHeaders.requireAdmin(role);
        return books.create(request);
    }

    @PutMapping("/api/admin/books/{id}")
    public BookView update(@RequestHeader(CallerHeaders.USER_ROLE) String role, @PathVariable Long id,
                           @Valid @RequestBody BookRequest request) {
        CallerHeaders.requireAdmin(role);
        return books.update(id, request);
    }

    /** Deletes the book, or retires it if it has been sold. */
    @DeleteMapping("/api/admin/books/{id}")
    public Map<String, Object> remove(@RequestHeader(CallerHeaders.USER_ROLE) String role, @PathVariable Long id) {
        CallerHeaders.requireAdmin(role);
        boolean deleted = books.remove(id);
        return Map.of("id", id, "result", deleted ? "deleted" : "retired");
    }
}
