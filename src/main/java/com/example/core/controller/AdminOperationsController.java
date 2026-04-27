package com.example.core.controller;

import com.example.core.dto.AssignOrderRequest;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.model.User;
import com.example.core.service.BiMetricsService;
import com.example.core.service.DispatchPolicyService;
import com.example.core.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationsController {

    private final DispatchPolicyService dispatchPolicyService;
    private final BiMetricsService biMetricsService;
    private final ReconciliationService reconciliationService;
    private final EntityDtoMapper entityDtoMapper;

    @GetMapping("/dispatch/policy")
    public ResponseEntity<?> dispatchPolicy(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(dispatchPolicyService.getPolicy());
    }

    @PatchMapping("/orders/{orderId}/assign")
    public ResponseEntity<?> assignOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @Valid @RequestBody AssignOrderRequest request
    ) {
        try {
            return ResponseEntity.ok(entityDtoMapper.toOrderResponse(
                    dispatchPolicyService.assignOrderManually(orderId, request.getCourierId(), currentUser, request.getReason())
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/bi/overview")
    public ResponseEntity<?> biOverview(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(biMetricsService.buildOverview());
    }

    @PostMapping("/reconciliation/run")
    public ResponseEntity<?> runReconciliation(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(reconciliationService.runNow());
    }

    @GetMapping("/reconciliation/last")
    public ResponseEntity<?> lastReconciliation(@AuthenticationPrincipal User currentUser) {
        var report = reconciliationService.getLastReport();
        if (report == null) {
            return ResponseEntity.ok(Map.of("message", "Пока нет выполненных reconciliation run"));
        }
        return ResponseEntity.ok(report);
    }
}
