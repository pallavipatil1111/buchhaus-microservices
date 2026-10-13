package com.buchhaus.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A title in the catalogue. {@code version} gives optimistic locking for admin edits; checkout
 * additionally takes a row lock (SELECT ... FOR UPDATE) so two shoppers can never buy the last copy.
 * Books that have been ordered are retired ({@code active = false}) instead of deleted.
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String sku;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 120)
    private String author;

    @Column(nullable = false, length = 40)
    private String genre;

    @Column(length = 2000)
    private String description;

    @Column(name = "price_paise", nullable = false)
    private long pricePaise;

    @Column(nullable = false)
    private int stock;

    @Column(name = "published_year")
    private Integer publishedYear;

    private Integer pages;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Book() {
    }

    public Book(String sku, String title, String author, String genre, long pricePaise, int stock) {
        this.sku = sku;
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.pricePaise = pricePaise;
        this.stock = stock;
    }

    /** Takes copies out of stock; the caller has already checked availability under a row lock. */
    public void removeStock(int quantity) {
        if (quantity <= 0 || quantity > stock) {
            throw new IllegalStateException("Not enough stock for " + title);
        }
        stock -= quantity;
    }

    public void restock(int quantity) {
        stock += quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public Integer getPublishedYear() {
        return publishedYear;
    }

    public void setPublishedYear(Integer publishedYear) {
        this.publishedYear = publishedYear;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
