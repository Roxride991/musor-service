package com.example.core.repository;

import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findByClient(User client);
    
    List<Order> findByCourier(User courier);

    List<Order> findByStatus(OrderStatus status);
    
    Optional<Order> findByIdAndClient(Long id, User client);
    
    Optional<Order> findByIdAndCourier(Long id, User courier);

    List<Order> findByStatusAndCourierIsNull(OrderStatus status);

    long countByStatus(OrderStatus status);

    long countByStatusInAndPickupTimeBefore(List<OrderStatus> statuses, java.time.OffsetDateTime time);

    long countByStatusInAndPickupTimeBetween(
            List<OrderStatus> statuses,
            java.time.OffsetDateTime from,
            java.time.OffsetDateTime to
    );

    long countByCourierAndStatusIn(User courier, List<OrderStatus> statuses);

    List<Order> findByStatusInAndPickupTimeBetween(
            List<OrderStatus> statuses,
            java.time.OffsetDateTime from,
            java.time.OffsetDateTime to
    );

    boolean existsBySubscriptionIdAndStatusIn(Long subscriptionId, List<OrderStatus> statuses);

    Optional<Order> findFirstBySubscriptionIdAndStatusOrderByPickupTimeAsc(Long subscriptionId, OrderStatus status);

    @Query(
            value = """
                    SELECT o.address
                    FROM orders o
                    WHERE lower(o.address) LIKE lower(concat('%', :query, '%'))
                    GROUP BY o.address
                    ORDER BY MAX(o.created_at) DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<String> findAddressHistorySuggestions(
            @Param("query") String query,
            @Param("limit") int limit
    );

    /**
     * Находит заказ по ID с пессимистической блокировкой для предотвращения race condition.
     * Используется SELECT FOR UPDATE для блокировки строки в БД до завершения транзакции.
     *
     * @param id ID заказа
     * @return Optional с заказом, если найден
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);
}


