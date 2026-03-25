package com.zeta.pec.mailbox;

import com.zeta.pec.mailbox.dto.ActivateMailboxRequest;
import com.zeta.pec.mailbox.dto.MailboxResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mailboxes")
@RequiredArgsConstructor
public class MailboxController {

    private final MailboxService mailboxService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public MailboxResponse activateMailbox(@Valid @RequestBody ActivateMailboxRequest request) {
        return mailboxService.activateMailbox(request.getUserId(), request);
    }

    @GetMapping("/{id}")
    public MailboxResponse getMailbox(@PathVariable UUID id) {
        return mailboxService.getMailbox(id);
    }

    @GetMapping("/user/{userId}")
    public List<MailboxResponse> getUserMailboxes(@PathVariable UUID userId) {
        return mailboxService.getUserMailboxes(userId);
    }
}
