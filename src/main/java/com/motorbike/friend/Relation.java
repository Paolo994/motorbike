package com.motorbike.friend;

/** Relazione tra l'utente corrente e un altro utente, dal punto di vista dell'utente corrente. */
public enum Relation {
    SELF,
    NONE,
    FRIENDS,
    /** ho inviato io la richiesta, in attesa */
    OUTGOING,
    /** ho ricevuto la richiesta, devo rispondere */
    INCOMING
}
