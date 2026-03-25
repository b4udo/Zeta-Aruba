package com.zeta.pec.integration;

public record ArubaPecSendRequest(
        String senderAddress,
        String recipientAddress,
        String subject,
        String body
) {
}
