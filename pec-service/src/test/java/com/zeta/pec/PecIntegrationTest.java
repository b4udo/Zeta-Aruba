package com.zeta.pec;

import com.zeta.pec.event.PecEventProducer;
import com.zeta.pec.mailbox.MailboxService;
import com.zeta.pec.mailbox.dto.ActivateMailboxRequest;
import com.zeta.pec.mailbox.dto.MailboxResponse;
import com.zeta.pec.message.MessageService;
import com.zeta.pec.message.dto.MessageResponse;
import com.zeta.pec.message.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class PecIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("zeta_pec_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:18080/realms/zeta");
    }

    @MockitoBean
    private PecEventProducer pecEventProducer;

    @Autowired
    private MailboxService mailboxService;

    @Autowired
    private MessageService messageService;

    @Test
    void fullMailboxAndMessageFlow() {
        UUID userId = UUID.randomUUID();

        ActivateMailboxRequest mailboxRequest = new ActivateMailboxRequest();
        mailboxRequest.setUserId(userId);
        mailboxRequest.setPecAddress("integration@pec.zeta.local");

        MailboxResponse mailbox = mailboxService.activateMailbox(userId, mailboxRequest);
        assertThat(mailbox.getId()).isNotNull();

        SendMessageRequest sendMessageRequest = new SendMessageRequest();
        sendMessageRequest.setRecipientAddress("destinatario@pec.example.it");
        sendMessageRequest.setSubject("Test integrazione");
        sendMessageRequest.setBody("Messaggio di test");

        MessageResponse message = messageService.sendMessage(mailbox.getId(), sendMessageRequest);

        assertThat(message.getId()).isNotNull();
        assertThat(message.getMailboxId()).isEqualTo(mailbox.getId());
        assertThat(message.getRecipientAddress()).isEqualTo("destinatario@pec.example.it");
    }
}
