package com.zeta.pec.integration;

import com.zeta.pec.message.MessageStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ArubaPecStub implements ArubaPecClient {

    @Override
    public List<ArubaPecMessageDto> fetchMessages(String mailboxAddress, int page, int size) {
        simulateLatency();
        log.info("Fetching PEC messages from Aruba stub: mailboxAddress={}, page={}, size={}", mailboxAddress, page, size);
        return List.of(
                new ArubaPecMessageDto(
                        UUID.randomUUID().toString(),
                        "fatture@pec.fornitore.it",
                        mailboxAddress,
                        "Fattura n. 2024/001",
                        "In allegato la fattura di marzo.",
                        MessageStatus.RECEIVED,
                        LocalDateTime.now().minusHours(2),
                        "fattura-2024-001.txt",
                        "Fattura Aruba demo".getBytes(StandardCharsets.UTF_8),
                        "text/plain"
                )
        );
    }

    @Override
    public ArubaPecMessageDto sendMessage(ArubaPecSendRequest request) {
        simulateLatency();
        log.info("Sending PEC message through Aruba stub: sender={}, recipient={}, subject={}",
                request.senderAddress(), request.recipientAddress(), request.subject());
        return new ArubaPecMessageDto(
                UUID.randomUUID().toString(),
                request.senderAddress(),
                request.recipientAddress(),
                request.subject(),
                request.body(),
                MessageStatus.SENT,
                LocalDateTime.now(),
                null,
                null,
                null
        );
    }

    @Override
    public ArubaPecMailboxStatus getMailboxStatus(String mailboxAddress) {
        simulateLatency();
        return ArubaPecMailboxStatus.ACTIVE;
    }

    private void simulateLatency() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
