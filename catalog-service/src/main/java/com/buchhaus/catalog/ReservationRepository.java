package com.buchhaus.catalog;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    /** Locks the reservation row so a confirm and a release for the same id cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> lockById(@Param("id") String id);

    @Query("select r.id from Reservation r where r.status = :status and r.createdAt < :before")
    List<String> findIdsByStatusCreatedBefore(@Param("status") ReservationStatus status, @Param("before") Instant before);

    /** Has this book ever been sold? Decides between deleting and retiring it. */
    @Query("select count(l) > 0 from ReservationLine l where l.bookId = :bookId and l.reservation.status <> :released")
    boolean wasSold(@Param("bookId") Long bookId, @Param("released") ReservationStatus released);
}
