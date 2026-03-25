package com.zeta.pec.message.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank
    private String recipientAddress;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    private String attachmentFileName;
    private String attachmentBase64;
    private String attachmentContentType;
}
