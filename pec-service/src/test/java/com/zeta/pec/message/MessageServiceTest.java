package com.zeta.pec.message;

import com.zeta.pec.config.MinioProperties;
import com.zeta.pec.event.PecEventProducer;
import com.zeta.pec.integration.ArubaPecClient;
import com.zeta.pec.integration.ArubaPecMessageDto;
import com.zeta.pec.mailbox.Mailbox;
import com.zeta.pec.mailbox.MailboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeta.pec.message.dto.MessageResponse;
import com.zeta.pec.message.dto.SendMessageRequest;
import com.zeta.pec.storage.DocumentStorageService;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MailboxService mailboxService;

    @Mock
    private ArubaPecClient arubaPecClient;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private PecEventProducer pecEventProducer;

    private MinioProperties minioProperties;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        minioProperties = new MinioProperties();
        minioProperties.setBucket("pec-documents");
        messageService = new MessageService(
                messageRepository,
                mailboxService,
                arubaPecClient,
                documentStorageService,
                pecEventProducer,
                minioProperties
        );
    }

    @Test
    void sendMessage_shouldCallClientPersistAndPublishEvent() {
        UUID mailboxId = UUID.randomUUID();
        Mailbox mailbox = new Mailbox();
        mailbox.setId(mailboxId);
        mailbox.setPecAddress("sender@pec.zeta.local");

        SendMessageRequest request = new SendMessageRequest();
        request.setRecipientAddress("destinatario@pec.example.it");
        request.setSubject("Oggetto demo");
        request.setBody("Messaggio demo");

        ArubaPecMessageDto sentMessage = new ArubaPecMessageDto(
                UUID.randomUUID().toString(),
                mailbox.getPecAddress(),
                request.getRecipientAddress(),
                request.getSubject(),
                request.getBody(),
                MessageStatus.SENT,
                LocalDateTime.now(),
                null,
                null,
                null
        );

        when(mailboxService.getEntity(mailboxId)).thenReturn(mailbox);
        when(arubaPecClient.sendMessage(any())).thenReturn(sentMessage);
        when(messageRepository.save(any(PecMessage.class))).thenAnswer(invocation -> {
            PecMessage message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            return message;
        });

        MessageResponse response = messageService.sendMessage(mailboxId, request);

        assertThat(response.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(response.getRecipientAddress()).isEqualTo(request.getRecipientAddress());

        ArgumentCaptor<PecMessage> messageCaptor = ArgumentCaptor.forClass(PecMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        verify(pecEventProducer).publishMessageSent(any(PecMessage.class));

        PecMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getMailbox().getId()).isEqualTo(mailboxId);
        assertThat(savedMessage.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
    }
}
