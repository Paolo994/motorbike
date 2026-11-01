package com.motorbike.geo;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Proxy verso Nominatim: il browser non chiama servizi esterni e la policy d'uso viene rispettata lato server. */
@RestController
public class GeoController {

    private final GeocodingService geocoding;

    public GeoController(GeocodingService geocoding) {
        this.geocoding = geocoding;
    }

    @GetMapping("/api/geocode")
    public List<GeocodingService.Place> geocode(@RequestParam String q) {
        return geocoding.search(q);
    }
}
