package com.example.core.repository;

import com.example.core.model.ServiceZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceZoneRepository extends JpaRepository<ServiceZone, Long> {

    Optional<ServiceZone> findFirstByActiveTrue();

    List<ServiceZone> findByActiveTrue();
}