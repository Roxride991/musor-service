package com.example.core.controller;

import com.example.core.dto.AuditEventResponse;
import com.example.core.dto.CreateSubscriptionRequest;
import com.example.core.dto.ExtendSubscriptionRequest;
import com.example.core.dto.RescheduleSubscriptionRequest;
import com.example.core.dto.SubscriptionResponse;
import com.example.core.dto.UpdateSubscriptionAddressRequest;
import com.example.core.dto.UpdateSubscriptionSlotRequest;
import com.example.core.model.User;
import com.example.core.service.SubscriptionFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionFacadeService subscriptionFacadeService;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionFacadeService.createSubscription(currentUser, request));
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptions(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(subscriptionFacadeService.getSubscriptions(currentUser));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionResponse>> getActiveSubscriptions(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(subscriptionFacadeService.getManageableSubscriptions(currentUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id
    ) {
        subscriptionFacadeService.cancelSubscription(currentUser, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/pause")
    public ResponseEntity<SubscriptionResponse> pauseSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(subscriptionFacadeService.pauseSubscription(currentUser, id));
    }

    @PatchMapping("/{id}/resume")
    public ResponseEntity<SubscriptionResponse> resumeSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(subscriptionFacadeService.resumeSubscription(currentUser, id));
    }

    @PatchMapping("/{id}/skip-next")
    public ResponseEntity<SubscriptionResponse> skipNextPickup(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(subscriptionFacadeService.skipNextPickup(currentUser, id));
    }

    @PatchMapping("/{id}/address")
    public ResponseEntity<SubscriptionResponse> updateAddress(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubscriptionAddressRequest request
    ) {
        return ResponseEntity.ok(subscriptionFacadeService.updateAddress(currentUser, id, request));
    }

    @PatchMapping("/{id}/slot")
    public ResponseEntity<SubscriptionResponse> updateSlot(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSubscriptionSlotRequest request
    ) {
        return ResponseEntity.ok(subscriptionFacadeService.updateSlot(currentUser, id, request));
    }

    @PatchMapping("/{id}/extend")
    public ResponseEntity<SubscriptionResponse> extendSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) ExtendSubscriptionRequest request
    ) {
        return ResponseEntity.ok(subscriptionFacadeService.extendSubscription(currentUser, id, request));
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<SubscriptionResponse> rescheduleSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) RescheduleSubscriptionRequest request
    ) {
        return ResponseEntity.ok(subscriptionFacadeService.rescheduleSubscription(currentUser, id, request));
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<AuditEventResponse>> subscriptionTimeline(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(subscriptionFacadeService.getTimeline(currentUser, id, limit));
    }
}
