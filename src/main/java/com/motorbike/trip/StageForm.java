package com.motorbike.trip;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.motorbike.trip.TripEnums.AccommodationType;
import com.motorbike.trip.TripEnums.StopType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class StageForm {

    static final int MAX_STOPS = 15;

    @NotBlank(message = "Dai un titolo alla tappa")
    @Size(max = 120)
    private String title;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate stageDate;

    @NotBlank(message = "Scegli il punto di partenza")
    @Size(max = 200)
    private String startName;
    @NotNull(message = "Scegli il punto di partenza dalla ricerca o sulla mappa")
    @DecimalMin("-90") @DecimalMax("90")
    private Double startLat;
    @NotNull(message = "Scegli il punto di partenza dalla ricerca o sulla mappa")
    @DecimalMin("-180") @DecimalMax("180")
    private Double startLon;

    @NotBlank(message = "Scegli il punto di arrivo")
    @Size(max = 200)
    private String endName;
    @NotNull(message = "Scegli il punto di arrivo dalla ricerca o sulla mappa")
    @DecimalMin("-90") @DecimalMax("90")
    private Double endLat;
    @NotNull(message = "Scegli il punto di arrivo dalla ricerca o sulla mappa")
    @DecimalMin("-180") @DecimalMax("180")
    private Double endLon;

    private boolean avoidMotorways;
    private boolean avoidTolls;

    @Size(max = 4000)
    private String notes;
    @Size(max = 4000)
    private String highlights;

    private AccommodationType accommodationType;
    @Size(max = 150)
    private String accommodationName;
    @Size(max = 250)
    private String accommodationAddress;
    @DecimalMin(value = "0", message = "Il prezzo non può essere negativo")
    @DecimalMax(value = "100000")
    private BigDecimal accommodationPrice;
    @Size(max = 500)
    @Pattern(regexp = "^$|^https?://\\S+$", message = "Inserisci un indirizzo web che inizi con http:// o https://")
    private String accommodationUrl;

    @Valid
    @Size(max = MAX_STOPS, message = "Al massimo " + MAX_STOPS + " fermate per tappa")
    private List<StopForm> stops = new ArrayList<>();

    public static class StopForm {
        @Size(max = 150)
        private String name;
        private StopType type = StopType.OTHER;
        private Double lat;
        private Double lon;
        @Size(max = 500)
        private String notes;

        /** Le righe lasciate vuote nel form vengono ignorate. */
        boolean isFilled() {
            return lat != null && lon != null;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public StopType getType() { return type; }
        public void setType(StopType type) { this.type = type; }
        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }
        public Double getLon() { return lon; }
        public void setLon(Double lon) { this.lon = lon; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    static StageForm from(TripStage s) {
        StageForm f = new StageForm();
        f.title = s.getTitle();
        f.stageDate = s.getStageDate();
        f.startName = s.getStartName();
        f.startLat = s.getStartLat();
        f.startLon = s.getStartLon();
        f.endName = s.getEndName();
        f.endLat = s.getEndLat();
        f.endLon = s.getEndLon();
        f.avoidMotorways = s.isAvoidMotorways();
        f.avoidTolls = s.isAvoidTolls();
        f.notes = s.getNotes();
        f.highlights = s.getHighlights();
        f.accommodationType = s.getAccommodationType();
        f.accommodationName = s.getAccommodationName();
        f.accommodationAddress = s.getAccommodationAddress();
        f.accommodationPrice = s.getAccommodationPrice();
        f.accommodationUrl = s.getAccommodationUrl();
        for (TripStop stop : s.getStops()) {
            StopForm sf = new StopForm();
            sf.name = stop.getName();
            sf.type = stop.getType();
            sf.lat = stop.getLat();
            sf.lon = stop.getLon();
            sf.notes = stop.getNotes();
            f.stops.add(sf);
        }
        return f;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getStageDate() { return stageDate; }
    public void setStageDate(LocalDate stageDate) { this.stageDate = stageDate; }
    public String getStartName() { return startName; }
    public void setStartName(String startName) { this.startName = startName; }
    public Double getStartLat() { return startLat; }
    public void setStartLat(Double startLat) { this.startLat = startLat; }
    public Double getStartLon() { return startLon; }
    public void setStartLon(Double startLon) { this.startLon = startLon; }
    public String getEndName() { return endName; }
    public void setEndName(String endName) { this.endName = endName; }
    public Double getEndLat() { return endLat; }
    public void setEndLat(Double endLat) { this.endLat = endLat; }
    public Double getEndLon() { return endLon; }
    public void setEndLon(Double endLon) { this.endLon = endLon; }
    public boolean isAvoidMotorways() { return avoidMotorways; }
    public void setAvoidMotorways(boolean avoidMotorways) { this.avoidMotorways = avoidMotorways; }
    public boolean isAvoidTolls() { return avoidTolls; }
    public void setAvoidTolls(boolean avoidTolls) { this.avoidTolls = avoidTolls; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getHighlights() { return highlights; }
    public void setHighlights(String highlights) { this.highlights = highlights; }
    public AccommodationType getAccommodationType() { return accommodationType; }
    public void setAccommodationType(AccommodationType t) { this.accommodationType = t; }
    public String getAccommodationName() { return accommodationName; }
    public void setAccommodationName(String v) { this.accommodationName = v; }
    public String getAccommodationAddress() { return accommodationAddress; }
    public void setAccommodationAddress(String v) { this.accommodationAddress = v; }
    public BigDecimal getAccommodationPrice() { return accommodationPrice; }
    public void setAccommodationPrice(BigDecimal v) { this.accommodationPrice = v; }
    public String getAccommodationUrl() { return accommodationUrl; }
    public void setAccommodationUrl(String v) { this.accommodationUrl = v; }
    public List<StopForm> getStops() { return stops; }
    public void setStops(List<StopForm> stops) { this.stops = stops; }
}
