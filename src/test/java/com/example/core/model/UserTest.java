package com.example.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void userDetailsMethodsShouldHandleNullBannedFlag() {
        User user = User.builder()
                .phone("+79990000000")
                .name("Test User")
                .password("secret")
                .userRole(UserRole.CLIENT)
                .build();
        user.setBanned(null);

        assertDoesNotThrow(user::isAccountNonLocked);
        assertDoesNotThrow(user::isEnabled);
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isEnabled());
    }
}
