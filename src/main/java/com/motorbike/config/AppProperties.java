package com.motorbike.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String baseUrl, Mail mail, Verification verification, Admin admin, Http http,
                            News news, Fuel fuel, Routing routing, Geocoding geocoding, Ai ai, Places places) {

    /**
     * Modalità AI (consigli per le tappe). La chiave Anthropic si legge dalla variabile ANTHROPIC_API_KEY.
     *
     * @param enabled              interruttore generale; se false la funzione non compare nell'interfaccia
     * @param maxRequestsPerHour   consigli generabili da un utente in un'ora (controllo dei costi)
     */
    public record Ai(boolean enabled, String model, String effort, int maxRequestsPerHour, Duration cacheTtl) {
    }

    /** Google Places API (New): luoghi reali con valutazioni e recensioni. */
    public record Places(String apiKey, String baseUrl) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Mail(String from) {
    }

    public record Verification(Duration tokenValidity) {
    }

    public record Admin(String username, String email, String password) {
    }

    public record Http(String userAgent) {
    }

    public record News(boolean fetchEnabled, Duration fetchInterval, Duration notifyMaxAge) {
    }

    public record Fuel(boolean importEnabled, String importCron, String stationsUrl, String pricesUrl) {
    }

    public record Routing(String valhallaUrl) {
    }

    public record Geocoding(String nominatimUrl) {
    }
}
