package com.zeta.pec.message;

import com.zeta.pec.message.dto.MessageResponse;
import com.zeta.pec.message.dto.SendMessageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mailboxes/{mailboxId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'OPERATOR')")
    public MessageResponse sendMessage(@PathVariable UUID mailboxId,
                                       @Valid @RequestBody SendMessageRequest request) {
        return messageService.sendMessage(mailboxId, request);
    }

    @GetMapping
    public Page<MessageResponse> getMessages(@PathVariable UUID mailboxId, Pageable pageable) {
        return messageService.getMessages(mailboxId, pageable);
    }

    @GetMapping("/{messageId}")
    public MessageResponse getMessage(@PathVariable UUID messageId) {
        return messageService.getMessage(messageId);
    }

    @PostMapping("/sync")
    public void syncMessages(@PathVariable UUID mailboxId) {
        messageService.syncMessages(mailboxId);
    }
}
