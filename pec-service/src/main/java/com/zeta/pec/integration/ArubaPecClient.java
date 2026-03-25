package com.zeta.pec.integration;

import java.util.List;

public interface ArubaPecClient {

    List<ArubaPecMessageDto> fetchMessages(String mailboxAddress, int page, int size);

    ArubaPecMessageDto sendMessage(ArubaPecSendRequest request);

    ArubaPecMailboxStatus getMailboxStatus(String mailboxAddress);
}
