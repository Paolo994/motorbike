package com.motorbike.chat;

import java.time.OffsetDateTime;
import java.util.Locale;

/** Riga della lista conversazioni. Mappata direttamente dalla query SQL in {@link ChatService#inbox}. */
public record ConversationSummary(long id, String otherUsername, String otherName, Long otherPhotoId,
                                  String lastBody, boolean lastFromMe, OffsetDateTime lastMessageAt, long unread) {

    /** true se username o nome visualizzato contengono il testo cercato (già in minuscolo). */
    boolean matches(String lowerQuery) {
        return otherUsername.toLowerCase(Locale.ROOT).contains(lowerQuery)
            || otherName.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }
}
