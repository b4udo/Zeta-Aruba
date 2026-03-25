package com.zeta.pec.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<PecMessage, UUID> {

    Page<PecMessage> findByMailboxId(UUID mailboxId, Pageable pageable);

    boolean existsByExternalMessageId(String externalMessageId);
}
