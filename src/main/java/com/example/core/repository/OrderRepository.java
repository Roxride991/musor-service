package com.example.core.repository;

import com.example.core.model.Order;
import com.example.core.model.OrderStatus;
import com.example.core.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByClient(User client);
    
    List<Order> findByCourier(User courier);
    
    Optional<Order> findByIdAndClient(Long id, User client);
    
    Optional<Order> findByIdAndCourier(Long id, User courier);

    List<Order> findByStatusAndCourierIsNull(OrderStatus status);

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


