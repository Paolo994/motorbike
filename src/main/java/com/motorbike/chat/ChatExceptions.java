package com.motorbike.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Eccezioni del modulo chat; il messaggio è mostrato all'utente. */
public final class ChatExceptions {

    private ChatExceptions() {
    }

    /** Operazione non consentita (es. scrivere a chi non è più amico). */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class NotAllowed extends RuntimeException {
        public NotAllowed(String message) {
            super(message);
        }
    }

    /** Testo del messaggio vuoto o troppo lungo. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidMessage extends RuntimeException {
        public InvalidMessage(String message) {
            super(message);
        }
    }

    /** Conversazione inesistente o di cui l'utente non fa parte (non distinguiamo i due casi). */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ConversationNotFound extends RuntimeException {
        public ConversationNotFound(Long id) {
            super("Conversazione non trovata: " + id);
        }
    }
}
