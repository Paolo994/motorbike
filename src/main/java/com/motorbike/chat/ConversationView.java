package com.motorbike.chat;

import java.util.List;

/** Dati della pagina di una conversazione. */
public record ConversationView(long id, String otherUsername, String otherName, Long otherPhotoId, boolean canSend,
                               List<MessageDto> messages, boolean hasOlder) {
}
