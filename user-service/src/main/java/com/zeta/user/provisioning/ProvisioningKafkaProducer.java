package com.zeta.user.provisioning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProvisioningKafkaProducer {

    private static final String TOPIC = "user.service.activated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishServiceActivated(UUID userId, String serviceName) {
        var event = Map.of(
                "userId", userId.toString(),
                "service", serviceName,
                "action", "ACTIVATED"
        );
        kafkaTemplate.send(TOPIC, userId.toString(), event);
        log.info("Published service activated event: userId={}, service={}", userId, serviceName);
    }
}
