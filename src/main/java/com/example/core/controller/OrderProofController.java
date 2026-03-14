package com.example.core.controller;

import com.example.core.dto.UploadProofPhotoRequest;
import com.example.core.model.User;
import com.example.core.service.OrderProofService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderProofController {

    private final OrderProofService orderProofService;

    @PostMapping("/{orderId}/proofs")
    public ResponseEntity<?> uploadProof(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @Valid @RequestBody UploadProofPhotoRequest request
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(orderProofService.uploadProof(orderId, currentUser, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{orderId}/proofs")
    public ResponseEntity<?> listProofs(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId
    ) {
        try {
            return ResponseEntity.ok(orderProofService.listProofs(orderId, currentUser));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{orderId}/proofs/{proofId}/content")
    public ResponseEntity<?> getProofContent(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @PathVariable Long proofId
    ) {
        try {
            OrderProofService.ProofContent content = orderProofService.loadProofContent(orderId, proofId, currentUser);
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(content.getMimeType());
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentDisposition(ContentDisposition.inline().filename("proof-" + proofId).build());
            return new ResponseEntity<>(content.getBytes(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }
}
