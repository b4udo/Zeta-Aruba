package com.zeta.pec.event;

import com.zeta.pec.mailbox.MailboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PecEventConsumer {

    private final MailboxService mailboxService;

    @KafkaListener(topics = "user.service.activated", groupId = "pec-service")
    public void onUserServiceActivated(Map<String, Object> event) {
        String service = String.valueOf(event.getOrDefault("service", ""));
        String action = String.valueOf(event.getOrDefault("action", ""));
        if (!"PEC".equalsIgnoreCase(service) || !"ACTIVATED".equalsIgnoreCase(action)) {
            return;
        }

        UUID userId = UUID.fromString(String.valueOf(event.get("userId")));
        mailboxService.activateAutomaticMailbox(userId);
        log.info("Auto-provisioned PEC mailbox after user service activation: userId={}", userId);
    }
}
