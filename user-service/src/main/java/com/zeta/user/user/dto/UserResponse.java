package com.zeta.user.user.dto;

import com.zeta.user.user.UserRole;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
public class UserResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String organization;
    private UserRole role;
    private Set<String> activatedServices;
    private boolean semanticIndexingEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
