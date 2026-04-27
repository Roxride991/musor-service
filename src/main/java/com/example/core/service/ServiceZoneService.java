package com.example.core.service;

import com.example.core.dto.CreateServiceZoneRequest;
import com.example.core.exception.ForbiddenOperationException;
import com.example.core.exception.ResourceNotFoundException;
import com.example.core.model.ServiceZone;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.ServiceZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceZoneService {

    private final ServiceZoneRepository serviceZoneRepository;

    @Transactional(readOnly = true)
    public ServiceZone getActiveZone() {
        return serviceZoneRepository.findFirstByActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("Активная зона обслуживания не настроена"));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ServiceZone replaceActiveZone(User currentUser, CreateServiceZoneRequest request) {
        if (currentUser == null || currentUser.getUserRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException("Только администратор может изменять зону обслуживания");
        }

        serviceZoneRepository.deactivateAllActiveZones();

        List<ServiceZone.Coordinate> coordinates = new ArrayList<>(request.getCoordinates().stream()
                .map(dto -> new ServiceZone.Coordinate(dto.getLat(), dto.getLng()))
                .toList());

        if (!coordinates.isEmpty()) {
            ServiceZone.Coordinate first = coordinates.get(0);
            ServiceZone.Coordinate last = coordinates.get(coordinates.size() - 1);
            double latDiff = Math.abs(first.getLat() - last.getLat());
            double lngDiff = Math.abs(first.getLng() - last.getLng());
            if (latDiff > 1e-9 || lngDiff > 1e-9) {
                coordinates.add(new ServiceZone.Coordinate(first.getLat(), first.getLng()));
            }
        }

        ServiceZone zone = ServiceZone.builder()
                .name(request.getName().trim())
                .coordinates(coordinates)
                .active(true)
                .build();

        return serviceZoneRepository.save(zone);
    }
}
