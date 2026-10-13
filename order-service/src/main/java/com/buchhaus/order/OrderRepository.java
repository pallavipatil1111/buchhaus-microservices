package com.buchhaus.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

    List<CustomerOrder> findAllByOrderByCreatedAtDescIdDesc();

    List<CustomerOrder> findByStatusOrderByCreatedAtDescIdDesc(OrderStatus status);

    List<CustomerOrder> findTop50ByStockConfirmedFalseAndStatusNotOrderByIdAsc(OrderStatus status);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.totalPaise), 0) from CustomerOrder o where o.status <> :excluded")
    long revenueExcluding(@Param("excluded") OrderStatus excluded);

    @Query("select new com.buchhaus.order.TopSeller(i.bookId, i.titleSnapshot, sum(i.quantity)) "
            + "from OrderItem i where i.order.status <> :excluded "
            + "group by i.bookId, i.titleSnapshot order by sum(i.quantity) desc")
    List<TopSeller> topSellers(@Param("excluded") OrderStatus excluded, Pageable pageable);
}
