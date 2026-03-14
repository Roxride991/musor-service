package com.example.core.controller;

import com.example.core.model.User;
import com.example.core.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> getNotifications(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(notificationService.listForUser(currentUser, limit));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long notificationId
    ) {
        try {
            return ResponseEntity.ok(notificationService.markAsRead(currentUser, notificationId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }
}
