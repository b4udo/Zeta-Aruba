package com.zeta.pec.event;

import com.zeta.pec.message.PecMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PecEventProducer {

    private static final String TOPIC = "pec.message.sent";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishMessageSent(PecMessage message) {
        Map<String, Object> event = Map.of(
                "messageId", message.getId().toString(),
                "mailboxId", message.getMailbox().getId().toString(),
                "status", message.getStatus().name(),
                "recipientAddress", message.getRecipientAddress()
        );
        kafkaTemplate.send(TOPIC, message.getId().toString(), event);
        log.info("Published PEC sent event: messageId={}, mailboxId={}", message.getId(), message.getMailbox().getId());
    }
}
