package com.motorbike.trip;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class TripExceptions {

    private TripExceptions() {
    }

    /** Viaggio o tappa inesistente, oppure l'utente non ne fa parte (non distinguiamo i due casi). */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class TripNotFound extends RuntimeException {
        public TripNotFound() {
            super("Viaggio non trovato");
        }
    }

    /** L'utente fa parte del viaggio ma il suo ruolo non consente l'operazione. */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class NotAllowed extends RuntimeException {
        public NotAllowed(String message) {
            super(message);
        }
    }

    /** Operazione non valida nello stato attuale; il messaggio è mostrato all'utente. */
    public static class InvalidOperation extends RuntimeException {
        public InvalidOperation(String message) {
            super(message);
        }
    }
}
