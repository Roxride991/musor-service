package com.example.core.controller;

import com.example.core.dto.CreateSubscriptionRequest;
import com.example.core.dto.SubscriptionResponse;
import com.example.core.dto.mapper.DtoMapper;
import com.example.core.model.User;
import com.example.core.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер подписок: создание, получение списка, отмена, пауза, возобновление.
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final DtoMapper dtoMapper;

    /** Внедрение сервиса подписок и маппера DTO. */
    public SubscriptionController(SubscriptionService subscriptionService, DtoMapper dtoMapper) {
        this.subscriptionService = subscriptionService;
        this.dtoMapper = dtoMapper;
    }

    /** Оформить новую подписку текущему пользователю. */
    @PostMapping
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateSubscriptionRequest request) {

        try {
            var subscription = subscriptionService.createSubscription(currentUser, request.getPlan());
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.status(201).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Получить все подписки текущего пользователя. */
    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptions(
            @AuthenticationPrincipal User currentUser) {

        var subscriptions = subscriptionService.getSubscriptionsForUser(currentUser);
        var responses = subscriptions.stream()
                .map(dtoMapper::toSubscriptionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /** Получить только активные подписки текущего пользователя. */
    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionResponse>> getActiveSubscriptions(
            @AuthenticationPrincipal User currentUser) {

        var subscriptions = subscriptionService.getActiveSubscriptionsForUser(currentUser);
        var responses = subscriptions.stream()
                .map(dtoMapper::toSubscriptionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /** Отменить подписку немедленно (статус CANCELED). */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        try {
            subscriptionService.cancelSubscription(id, currentUser);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Приостановить активную подписку (статус PAUSED). */
    @PatchMapping("/{id}/pause")
    public ResponseEntity<SubscriptionResponse> pauseSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        try {
            var subscription = subscriptionService.pauseSubscription(id, currentUser);
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Возобновить приостановленную подписку (статус ACTIVE). */
    @PatchMapping("/{id}/resume")
    public ResponseEntity<SubscriptionResponse> resumeSubscription(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {

        try {
            var subscription = subscriptionService.resumeSubscription(id, currentUser);
            var response = dtoMapper.toSubscriptionResponse(subscription);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}