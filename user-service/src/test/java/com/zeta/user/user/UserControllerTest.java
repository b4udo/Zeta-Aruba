package com.zeta.user.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeta.user.config.SecurityConfig;
import com.zeta.user.common.UserNotFoundException;
import com.zeta.user.user.dto.CreateUserRequest;
import com.zeta.user.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getUser_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUserById_shouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = new UserResponse();
        response.setId(id);
        response.setEmail("test@zeta.local");

        when(userService.getUserById(id)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@zeta.local"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUserById_notFound_shouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUserById(id)).thenThrow(new UserNotFoundException("Not found"));

        mockMvc.perform(get("/api/v1/users/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_valid_shouldReturn201() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new@zeta.local");
        request.setFirstName("Mario");
        request.setLastName("Rossi");

        UserResponse response = new UserResponse();
        response.setId(UUID.randomUUID());
        response.setEmail("new@zeta.local");

        when(userService.createUser(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@zeta.local"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_invalidEmail_shouldReturn400() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("not-an-email");
        request.setFirstName("Mario");
        request.setLastName("Rossi");

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createUser_missingFirstName_shouldReturn400() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("valid@zeta.local");

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createUser_nonAdmin_shouldReturn403() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new@zeta.local");
        request.setFirstName("Mario");
        request.setLastName("Rossi");

        mockMvc.perform(post("/api/v1/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUsers_shouldReturnPage() throws Exception {
        when(userService.getUsers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk());
    }
}
