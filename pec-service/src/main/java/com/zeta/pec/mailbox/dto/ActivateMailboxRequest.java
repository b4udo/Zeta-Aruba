package com.zeta.pec.mailbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ActivateMailboxRequest {

    @NotNull
    private UUID userId;

    @NotBlank
    private String pecAddress;
}
