package com.example.core.controller;

import com.example.core.dto.CreateServiceZoneRequest;
import com.example.core.model.ServiceZone;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.ServiceZoneRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/zones")
public class ServiceZoneController {

    private final ServiceZoneRepository zoneRepository;

    public ServiceZoneController(ServiceZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    @GetMapping("/active")
    public ResponseEntity<ServiceZone> getActiveZone() {
        return zoneRepository.findFirstByActiveTrue()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<ServiceZone> setActiveZone(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateServiceZoneRequest request) {

        if (currentUser == null || currentUser.getUserRole() != UserRole.ADMIN) {
            return ResponseEntity.status(403).build();
        }

        // Деактивировать все активные зоны атомарно внутри транзакции.
        zoneRepository.deactivateAllActiveZones();

        List<ServiceZone.Coordinate> coordinates = request.getCoordinates().stream()
                .map(dto -> new ServiceZone.Coordinate(dto.getLat(), dto.getLng()))
                .collect(Collectors.toList());

        // ✅ Автоматически замыкаем полигон
        if (!coordinates.isEmpty()) {
            var first = coordinates.get(0);
            var last = coordinates.get(coordinates.size() - 1);
            double latDiff = Math.abs(first.getLat() - last.getLat());
            double lngDiff = Math.abs(first.getLng() - last.getLng());
            if (latDiff > 1e-9 || lngDiff > 1e-9) {
                coordinates.add(new ServiceZone.Coordinate(first.getLat(), first.getLng()));
            }
        }

        ServiceZone zone = ServiceZone.builder()
                .name(request.getName())
                .coordinates(coordinates)
                .active(true)
                .build();

        zoneRepository.save(zone);
        return ResponseEntity.ok(zone);
    }
}
