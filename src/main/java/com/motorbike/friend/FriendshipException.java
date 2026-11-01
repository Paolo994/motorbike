package com.motorbike.friend;

/** Operazione sulle amicizie non consentita nello stato attuale; il messaggio è mostrato all'utente. */
public class FriendshipException extends RuntimeException {

    public FriendshipException(String message) {
        super(message);
    }
}
