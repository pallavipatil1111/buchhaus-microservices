package com.buchhaus.catalog;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Query("select b from Book b where b.active = true "
            + "and (:genre is null or b.genre = :genre) "
            + "and (:q is null or lower(b.title) like lower(concat('%', :q, '%')) "
            + "     or lower(b.author) like lower(concat('%', :q, '%')))")
    Page<Book> search(@Param("q") String q, @Param("genre") String genre, Pageable pageable);

    Optional<Book> findByIdAndActiveTrue(Long id);

    @Query("select distinct b.genre from Book b where b.active = true order by b.genre")
    List<String> findGenres();

    boolean existsBySkuIgnoreCase(String sku);

    /** Row locks for reservations: SELECT ... FOR UPDATE, always in id order to avoid deadlocks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id in :ids order by b.id")
    List<Book> lockAllById(@Param("ids") Collection<Long> ids);

    List<Book> findByIdIn(Collection<Long> ids);

    List<Book> findTop5ByActiveTrueAndStockLessThanOrderByStockAsc(int threshold);

    long countByActiveTrue();
}
