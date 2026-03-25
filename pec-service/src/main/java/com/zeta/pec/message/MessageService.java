package com.zeta.pec.message;

import com.zeta.pec.common.ResourceNotFoundException;
import com.zeta.pec.config.MinioProperties;
import com.zeta.pec.event.PecEventProducer;
import com.zeta.pec.integration.ArubaPecClient;
import com.zeta.pec.integration.ArubaPecMessageDto;
import com.zeta.pec.integration.ArubaPecSendRequest;
import com.zeta.pec.mailbox.Mailbox;
import com.zeta.pec.mailbox.MailboxService;
import com.zeta.pec.message.dto.MessageResponse;
import com.zeta.pec.message.dto.SendMessageRequest;
import com.zeta.pec.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final MailboxService mailboxService;
    private final ArubaPecClient arubaPecClient;
    private final DocumentStorageService documentStorageService;
    private final PecEventProducer pecEventProducer;
    private final MinioProperties minioProperties;

    @Transactional
    public MessageResponse sendMessage(UUID mailboxId, SendMessageRequest request) {
        Mailbox mailbox = mailboxService.getEntity(mailboxId);
        ArubaPecMessageDto remoteMessage = arubaPecClient.sendMessage(new ArubaPecSendRequest(
                mailbox.getPecAddress(),
                request.getRecipientAddress(),
                request.getSubject(),
                request.getBody()
        ));

        PecMessage message = new PecMessage();
        message.setMailbox(mailbox);
        message.setExternalMessageId(remoteMessage.externalMessageId());
        message.setSenderAddress(mailbox.getPecAddress());
        message.setRecipientAddress(request.getRecipientAddress());
        message.setSubject(request.getSubject());
        message.setBody(request.getBody());
        message.setDirection(MessageDirection.OUTBOUND);
        message.setStatus(remoteMessage.status());
        message.setReceivedAt(remoteMessage.receivedAt());

        if (request.getAttachmentBase64() != null && !request.getAttachmentBase64().isBlank()) {
            byte[] content = Base64.getDecoder().decode(request.getAttachmentBase64());
            String objectKey = mailboxId + "/" + UUID.randomUUID() + "-" + request.getAttachmentFileName();
            String documentKey = documentStorageService.uploadDocument(
                    minioProperties.getBucket(),
                    objectKey,
                    new ByteArrayInputStream(content),
                    request.getAttachmentContentType() != null ? request.getAttachmentContentType() : "application/octet-stream"
            );
            message.setDocumentKey(documentKey);
        }

        PecMessage saved = messageRepository.save(message);
        pecEventProducer.publishMessageSent(saved);
        log.info("Saved outbound PEC message: mailboxId={}, messageId={}", mailboxId, saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID mailboxId, Pageable pageable) {
        mailboxService.getEntity(mailboxId);
        return messageRepository.findByMailboxId(mailboxId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MessageResponse getMessage(UUID messageId) {
        return toResponse(messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("PEC message not found: " + messageId)));
    }

    @Transactional
    public void syncMessages(UUID mailboxId) {
        Mailbox mailbox = mailboxService.getEntity(mailboxId);
        for (ArubaPecMessageDto remoteMessage : arubaPecClient.fetchMessages(mailbox.getPecAddress(), 0, 20)) {
            if (messageRepository.existsByExternalMessageId(remoteMessage.externalMessageId())) {
                continue;
            }

            PecMessage message = new PecMessage();
            message.setMailbox(mailbox);
            message.setExternalMessageId(remoteMessage.externalMessageId());
            message.setSenderAddress(remoteMessage.senderAddress());
            message.setRecipientAddress(remoteMessage.recipientAddress());
            message.setSubject(remoteMessage.subject());
            message.setBody(remoteMessage.body());
            message.setDirection(MessageDirection.INBOUND);
            message.setStatus(remoteMessage.status());
            message.setReceivedAt(remoteMessage.receivedAt() != null ? remoteMessage.receivedAt() : LocalDateTime.now());

            if (remoteMessage.attachmentContent() != null && remoteMessage.attachmentFileName() != null) {
                String objectKey = mailboxId + "/sync-" + remoteMessage.externalMessageId() + "-" + remoteMessage.attachmentFileName();
                String documentKey = documentStorageService.uploadDocument(
                        minioProperties.getBucket(),
                        objectKey,
                        new ByteArrayInputStream(remoteMessage.attachmentContent()),
                        remoteMessage.attachmentContentType() != null ? remoteMessage.attachmentContentType() : "application/octet-stream"
                );
                message.setDocumentKey(documentKey);
            }

            messageRepository.save(message);
        }

        log.info("Completed PEC sync for mailboxId={}", mailboxId);
    }

    private MessageResponse toResponse(PecMessage message) {
        return MessageResponse.builder()
                .id(message.getId())
                .mailboxId(message.getMailbox().getId())
                .senderAddress(message.getSenderAddress())
                .recipientAddress(message.getRecipientAddress())
                .subject(message.getSubject())
                .body(message.getBody())
                .direction(message.getDirection())
                .status(message.getStatus())
                .documentKey(message.getDocumentKey())
                .receivedAt(message.getReceivedAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
