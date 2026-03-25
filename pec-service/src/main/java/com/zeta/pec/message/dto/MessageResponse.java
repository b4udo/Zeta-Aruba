package com.zeta.pec.message.dto;

import com.zeta.pec.message.MessageDirection;
import com.zeta.pec.message.MessageStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class MessageResponse {
    UUID id;
    UUID mailboxId;
    String senderAddress;
    String recipientAddress;
    String subject;
    String body;
    MessageDirection direction;
    MessageStatus status;
    String documentKey;
    LocalDateTime receivedAt;
    LocalDateTime createdAt;
}
