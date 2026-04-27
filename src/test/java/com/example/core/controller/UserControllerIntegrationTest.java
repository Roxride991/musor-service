package com.example.core.controller;

import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void getProfileShouldReturnAuthenticatedUserProfile() throws Exception {
        User currentUser = User.builder()
                .id(42L)
                .phone("+79990000042")
                .name("Timur")
                .password("encoded")
                .userRole(UserRole.CLIENT)
                .build();

        Mockito.when(userService.getProfile(currentUser)).thenReturn(currentUser);

        mockMvc.perform(get("/api/users/me")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                currentUser,
                                null,
                                currentUser.getAuthorities()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.phone").value("+79990000042"))
                .andExpect(jsonPath("$.name").value("Timur"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }
}
