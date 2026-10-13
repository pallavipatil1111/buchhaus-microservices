package com.buchhaus.order;

/** Copies sold per title, excluding cancelled orders (JPQL constructor projection). */
public record TopSeller(Long bookId, String title, Long copies) {
}
