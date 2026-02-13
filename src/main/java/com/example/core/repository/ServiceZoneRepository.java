package com.example.core.repository;

import com.example.core.model.ServiceZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceZoneRepository extends JpaRepository<ServiceZone, Long> {

    Optional<ServiceZone> findFirstByActiveTrue();

    List<ServiceZone> findByActiveTrue();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ServiceZone z SET z.active = false WHERE z.active = true")
    int deactivateAllActiveZones();
}
