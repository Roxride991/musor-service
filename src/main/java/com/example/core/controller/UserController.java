package com.example.core.controller;

import com.example.core.dto.UpdateNameRequest;
import com.example.core.dto.UserResponse;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.model.User;
import com.example.core.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EntityDtoMapper entityDtoMapper;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(entityDtoMapper.toUserResponse(userService.getProfile(currentUser)));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateName(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateNameRequest request
    ) {
        return ResponseEntity.ok(entityDtoMapper.toUserResponse(userService.updateName(currentUser, request.getName())));
    }
}
