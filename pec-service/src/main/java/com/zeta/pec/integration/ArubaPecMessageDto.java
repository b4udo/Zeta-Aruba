package com.zeta.pec.integration;

import com.zeta.pec.message.MessageStatus;

import java.time.LocalDateTime;

public record ArubaPecMessageDto(
        String externalMessageId,
        String senderAddress,
        String recipientAddress,
        String subject,
        String body,
        MessageStatus status,
        LocalDateTime receivedAt,
        String attachmentFileName,
        byte[] attachmentContent,
        String attachmentContentType
) {
}
