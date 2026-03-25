package com.zeta.pec.mailbox;

import com.zeta.pec.common.ConflictException;
import com.zeta.pec.common.ResourceNotFoundException;
import com.zeta.pec.mailbox.dto.ActivateMailboxRequest;
import com.zeta.pec.mailbox.dto.MailboxResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailboxService {

    private final MailboxRepository mailboxRepository;

    @Transactional
    public MailboxResponse activateMailbox(UUID userId, ActivateMailboxRequest request) {
        if (mailboxRepository.existsByPecAddress(request.getPecAddress())) {
            throw new ConflictException("Mailbox already exists for PEC address: " + request.getPecAddress());
        }

        Mailbox mailbox = new Mailbox();
        mailbox.setUserId(userId);
        mailbox.setPecAddress(request.getPecAddress().trim().toLowerCase());
        mailbox.setStatus(MailboxStatus.ACTIVE);

        Mailbox saved = mailboxRepository.save(mailbox);
        log.info("Activated PEC mailbox: userId={}, pecAddress={}", userId, saved.getPecAddress());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MailboxResponse getMailbox(UUID mailboxId) {
        return toResponse(getEntity(mailboxId));
    }

    @Transactional(readOnly = true)
    public List<MailboxResponse> getUserMailboxes(UUID userId) {
        return mailboxRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Mailbox getEntity(UUID mailboxId) {
        return mailboxRepository.findById(mailboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox not found: " + mailboxId));
    }

    @Transactional
    public MailboxResponse activateAutomaticMailbox(UUID userId) {
        String generatedAddress = "user-" + userId.toString().substring(0, 8) + "@pec.zeta.local";
        return mailboxRepository.findByPecAddress(generatedAddress)
                .map(this::toResponse)
                .orElseGet(() -> {
                    ActivateMailboxRequest request = new ActivateMailboxRequest();
                    request.setUserId(userId);
                    request.setPecAddress(generatedAddress);
                    return activateMailbox(userId, request);
                });
    }

    private MailboxResponse toResponse(Mailbox mailbox) {
        return MailboxResponse.builder()
                .id(mailbox.getId())
                .userId(mailbox.getUserId())
                .pecAddress(mailbox.getPecAddress())
                .status(mailbox.getStatus())
                .createdAt(mailbox.getCreatedAt())
                .updatedAt(mailbox.getUpdatedAt())
                .build();
    }
}
