package com.motorbike.chat;

import java.util.List;

/** Pubblicato dopo il salvataggio di un messaggio; consegnato via WebSocket dopo il commit. */
public record MessageSentEvent(MessageDto message, List<String> recipientUsernames) {
}
