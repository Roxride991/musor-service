package com.example.core.repository;

import com.example.core.model.OrderProofPhoto;
import com.example.core.model.ProofStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderProofPhotoRepository extends JpaRepository<OrderProofPhoto, Long> {

    List<OrderProofPhoto> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    boolean existsByOrderIdAndStage(Long orderId, ProofStage stage);

    Optional<OrderProofPhoto> findByIdAndOrderId(Long id, Long orderId);
}
