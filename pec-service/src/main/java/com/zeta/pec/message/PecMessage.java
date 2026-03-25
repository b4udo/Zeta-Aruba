package com.zeta.pec.message;

import com.zeta.pec.mailbox.Mailbox;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pec_messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class PecMessage {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mailbox_id")
    private Mailbox mailbox;

    private String externalMessageId;

    private String senderAddress;

    private String recipientAddress;

    private String subject;

    @Lob
    private String body;

    @Enumerated(EnumType.STRING)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    private String documentKey;

    private LocalDateTime receivedAt;

    @CreatedDate
    private LocalDateTime createdAt;
}
