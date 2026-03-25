package com.zeta.pec.mailbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailboxRepository extends JpaRepository<Mailbox, UUID> {

    Optional<Mailbox> findByPecAddress(String pecAddress);

    List<Mailbox> findByUserId(UUID userId);

    boolean existsByPecAddress(String pecAddress);
}
