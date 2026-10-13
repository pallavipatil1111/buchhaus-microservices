package com.buchhaus.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class BookServiceTest {

    @Test
    void soldBooksAreRetiredAndUnsoldBooksDeleted() {
        CatalogFixture f = new CatalogFixture();
        BookService books = new BookService(f.books, f.reservations);
        when(f.reservations.wasSold(eq(13L), any())).thenReturn(true);
        when(f.reservations.wasSold(eq(20L), any())).thenReturn(false);

        assertThat(books.remove(13L)).isFalse();
        assertThat(f.cleanCode.isActive()).isFalse();
        assertThat(books.remove(20L)).isTrue();
        verify(f.books).delete(f.ikigai);
    }
}
