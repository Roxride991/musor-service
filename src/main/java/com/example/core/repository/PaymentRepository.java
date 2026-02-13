package com.example.core.repository;

import com.example.core.model.Payment;
import com.example.core.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByOrderIdIn(Collection<Long> orderIds);

    List<Payment> findBySubscriptionId(Long subscriptionId);

    Optional<Payment> findByExternalId(String externalId);

    List<Payment> findByStatus(PaymentStatus status);
}
