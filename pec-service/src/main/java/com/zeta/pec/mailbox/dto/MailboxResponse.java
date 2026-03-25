package com.zeta.pec.mailbox.dto;

import com.zeta.pec.mailbox.MailboxStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class MailboxResponse {
    UUID id;
    UUID userId;
    String pecAddress;
    MailboxStatus status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
