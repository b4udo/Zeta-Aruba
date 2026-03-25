package com.zeta.pec.mailbox;

import com.zeta.pec.common.ConflictException;
import com.zeta.pec.mailbox.dto.ActivateMailboxRequest;
import com.zeta.pec.mailbox.dto.MailboxResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailboxServiceTest {

    @Mock
    private MailboxRepository mailboxRepository;

    @InjectMocks
    private MailboxService mailboxService;

    @Test
    void activateMailbox_shouldCreateMailbox() {
        UUID userId = UUID.randomUUID();
        ActivateMailboxRequest request = new ActivateMailboxRequest();
        request.setUserId(userId);
        request.setPecAddress("mario.rossi@pec.zeta.local");

        Mailbox saved = new Mailbox();
        saved.setId(UUID.randomUUID());
        saved.setUserId(userId);
        saved.setPecAddress(request.getPecAddress());
        saved.setStatus(MailboxStatus.ACTIVE);

        when(mailboxRepository.existsByPecAddress(request.getPecAddress())).thenReturn(false);
        when(mailboxRepository.save(any(Mailbox.class))).thenReturn(saved);

        MailboxResponse response = mailboxService.activateMailbox(userId, request);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getPecAddress()).isEqualTo(request.getPecAddress());
        assertThat(response.getStatus()).isEqualTo(MailboxStatus.ACTIVE);
    }

    @Test
    void activateMailbox_shouldRejectDuplicateAddress() {
        UUID userId = UUID.randomUUID();
        ActivateMailboxRequest request = new ActivateMailboxRequest();
        request.setUserId(userId);
        request.setPecAddress("duplicate@pec.zeta.local");

        when(mailboxRepository.existsByPecAddress(request.getPecAddress())).thenReturn(true);

        assertThatThrownBy(() -> mailboxService.activateMailbox(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Mailbox already exists");
    }

    @Test
    void getUserMailboxes_shouldReturnMailboxes() {
        UUID userId = UUID.randomUUID();
        Mailbox mailbox = new Mailbox();
        mailbox.setId(UUID.randomUUID());
        mailbox.setUserId(userId);
        mailbox.setPecAddress("user@pec.zeta.local");
        mailbox.setStatus(MailboxStatus.ACTIVE);

        when(mailboxRepository.findByUserId(userId)).thenReturn(List.of(mailbox));

        List<MailboxResponse> responses = mailboxService.getUserMailboxes(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getPecAddress()).isEqualTo("user@pec.zeta.local");
    }
}
