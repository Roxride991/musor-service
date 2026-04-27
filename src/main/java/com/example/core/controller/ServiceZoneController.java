package com.example.core.controller;

import com.example.core.dto.CreateServiceZoneRequest;
import com.example.core.dto.ServiceZoneResponse;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.model.User;
import com.example.core.service.ServiceZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
public class ServiceZoneController {

    private final ServiceZoneService serviceZoneService;
    private final EntityDtoMapper entityDtoMapper;

    @GetMapping("/active")
    public ResponseEntity<ServiceZoneResponse> getActiveZone() {
        return ResponseEntity.ok(entityDtoMapper.toServiceZoneResponse(serviceZoneService.getActiveZone()));
    }

    @PostMapping
    public ResponseEntity<ServiceZoneResponse> setActiveZone(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateServiceZoneRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(entityDtoMapper.toServiceZoneResponse(serviceZoneService.replaceActiveZone(currentUser, request)));
    }
}
