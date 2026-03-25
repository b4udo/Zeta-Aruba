package com.zeta.user.user.dto;

import com.zeta.user.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Email
    private String email;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    private String organization;

    private UserRole role;

    private Boolean semanticIndexingEnabled;
}
