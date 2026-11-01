package com.motorbike.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(String baseUrl, Mail mail, Verification verification, Admin admin, Http http,
                            News news, Fuel fuel, Routing routing, Geocoding geocoding) {

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
