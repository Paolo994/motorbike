package com.motorbike.auth;

/** Pubblicato quando serve inviare (o reinviare) l'email di verifica. */
public record VerificationMailRequested(String email, String username, String token) {
}
