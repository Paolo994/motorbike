package com.motorbike.auth;

/** Errore di registrazione legato a un campo del form (es. username già in uso). */
public class RegistrationException extends RuntimeException {

    private final String field;

    public RegistrationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
