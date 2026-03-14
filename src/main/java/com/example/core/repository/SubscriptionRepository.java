package com.example.core.repository;

import com.example.core.model.Subscription;
import com.example.core.model.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.id = :id")
    Optional<Subscription> findByIdWithLock(@Param("id") Long id);

    List<Subscription> findByStatusIn(List<SubscriptionStatus> statuses);

    List<Subscription> findByStatusInAndEndDateBefore(List<SubscriptionStatus> statuses, LocalDate date);

    long countByStatus(SubscriptionStatus status);

    @Query("""
            select count(s)
            from Subscription s
            where s.status = :status
              and not exists (
                  select 1
                  from Payment p
                  where p.subscription = s
                    and p.status = com.example.core.model.PaymentStatus.SUCCEEDED
              )
            """)
    long countWithoutSucceededPayment(@Param("status") SubscriptionStatus status);
}
