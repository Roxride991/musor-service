package com.example.core.repository;

import com.example.core.model.Payment;
import com.example.core.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdIn(Collection<Long> orderIds);

    List<Payment> findBySubscriptionId(Long subscriptionId);

    Optional<Payment> findByExternalId(String externalId);

    List<Payment> findByStatus(PaymentStatus status);

    Optional<Payment> findFirstByOrderId(Long orderId);

    Optional<Payment> findFirstBySubscriptionId(Long subscriptionId);

    List<Payment> findByStatusAndCreatedAtAfter(PaymentStatus status, OffsetDateTime createdAt);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = com.example.core.model.PaymentStatus.SUCCEEDED")
    java.math.BigDecimal sumSucceededAmount();

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = com.example.core.model.PaymentStatus.SUCCEEDED and p.createdAt >= :from")
    java.math.BigDecimal sumSucceededAmountFrom(@Param("from") OffsetDateTime from);

    @Query("""
            select p
            from Payment p
            left join p.order o
            left join o.client oc
            left join p.subscription s
            left join s.user su
            where oc.id = :userId or su.id = :userId
            order by p.createdAt desc
            """)
    List<Payment> findVisibleForUser(@Param("userId") Long userId);
}
