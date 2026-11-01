package com.motorbike.trip;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.motorbike.geo.LatLon;
import com.motorbike.geo.RouteResult;
import com.motorbike.trip.TripEnums.AccommodationType;
import com.motorbike.trip.TripEnums.RouteStatus;

@Entity
@Table(name = "trip_stages")
public class TripStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "stage_date")
    private LocalDate stageDate;

    @Column(name = "start_name", nullable = false, length = 200)
    private String startName;
    @Column(name = "start_lat", nullable = false)
    private double startLat;
    @Column(name = "start_lon", nullable = false)
    private double startLon;

    @Column(name = "end_name", nullable = false, length = 200)
    private String endName;
    @Column(name = "end_lat", nullable = false)
    private double endLat;
    @Column(name = "end_lon", nullable = false)
    private double endLon;

    @Column(name = "avoid_motorways", nullable = false)
    private boolean avoidMotorways;
    @Column(name = "avoid_tolls", nullable = false)
    private boolean avoidTolls;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_status", nullable = false, length = 20)
    private RouteStatus routeStatus = RouteStatus.PENDING;
    @Column(name = "distance_m")
    private Integer distanceM;
    @Column(name = "duration_s")
    private Integer durationS;
    @Column(name = "has_motorway")
    private Boolean hasMotorway;
    @Column(name = "has_toll")
    private Boolean hasToll;
    @Column(name = "route_shape", columnDefinition = "text")
    private String routeShape;
    @Column(name = "directions_json", columnDefinition = "text")
    private String directionsJson;

    @Column(length = 4000)
    private String notes;
    @Column(length = 4000)
    private String highlights;

    @Enumerated(EnumType.STRING)
    @Column(name = "accommodation_type", length = 20)
    private AccommodationType accommodationType;
    @Column(name = "accommodation_name", length = 150)
    private String accommodationName;
    @Column(name = "accommodation_address", length = 250)
    private String accommodationAddress;
    @Column(name = "accommodation_price", precision = 8, scale = 2)
    private BigDecimal accommodationPrice;
    @Column(name = "accommodation_url", length = 500)
    private String accommodationUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<TripStop> stops = new ArrayList<>();

    protected TripStage() {
    }

    TripStage(Trip trip, int position) {
        this.trip = trip;
        this.position = position;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /** Punti del percorso in ordine: partenza, fermate, arrivo. */
    public List<LatLon> waypoints() {
        List<LatLon> points = new ArrayList<>();
        points.add(new LatLon(startLat, startLon));
        stops.forEach(s -> points.add(new LatLon(s.getLat(), s.getLon())));
        points.add(new LatLon(endLat, endLon));
        return points;
    }

    void applyRoute(RouteResult r, String directionsJson) {
        this.routeStatus = r.estimated() ? RouteStatus.ESTIMATED : RouteStatus.OK;
        this.distanceM = r.distanceMeters();
        this.durationS = r.durationSeconds();
        this.hasMotorway = r.estimated() ? null : r.hasMotorway();
        this.hasToll = r.estimated() ? null : r.hasToll();
        this.routeShape = r.shape();
        this.directionsJson = directionsJson;
    }

    void replaceStops(List<TripStop> newStops) {
        stops.clear();
        stops.addAll(newStops);
    }

    void touch() {
        this.updatedAt = Instant.now();
    }

    // getter / setter

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public int getPosition() { return position; }
    public String getTitle() { return title; }
    public LocalDate getStageDate() { return stageDate; }
    public String getStartName() { return startName; }
    public double getStartLat() { return startLat; }
    public double getStartLon() { return startLon; }
    public String getEndName() { return endName; }
    public double getEndLat() { return endLat; }
    public double getEndLon() { return endLon; }
    public boolean isAvoidMotorways() { return avoidMotorways; }
    public boolean isAvoidTolls() { return avoidTolls; }
    public RouteStatus getRouteStatus() { return routeStatus; }
    public Integer getDistanceM() { return distanceM; }
    public Integer getDurationS() { return durationS; }
    public Boolean getHasMotorway() { return hasMotorway; }
    public Boolean getHasToll() { return hasToll; }
    public String getRouteShape() { return routeShape; }
    public String getDirectionsJson() { return directionsJson; }
    public String getNotes() { return notes; }
    public String getHighlights() { return highlights; }
    public AccommodationType getAccommodationType() { return accommodationType; }
    public String getAccommodationName() { return accommodationName; }
    public String getAccommodationAddress() { return accommodationAddress; }
    public BigDecimal getAccommodationPrice() { return accommodationPrice; }
    public String getAccommodationUrl() { return accommodationUrl; }
    public List<TripStop> getStops() { return stops; }

    void setPosition(int position) { this.position = position; }
    void setTitle(String title) { this.title = title; }
    void setStageDate(LocalDate stageDate) { this.stageDate = stageDate; }
    void setStart(String name, double lat, double lon) { this.startName = name; this.startLat = lat; this.startLon = lon; }
    void setEnd(String name, double lat, double lon) { this.endName = name; this.endLat = lat; this.endLon = lon; }
    void setAvoidMotorways(boolean v) { this.avoidMotorways = v; }
    void setAvoidTolls(boolean v) { this.avoidTolls = v; }
    void setNotes(String notes) { this.notes = notes; }
    void setHighlights(String highlights) { this.highlights = highlights; }
    void setAccommodation(AccommodationType type, String name, String address, BigDecimal price, String url) {
        this.accommodationType = type;
        this.accommodationName = name;
        this.accommodationAddress = address;
        this.accommodationPrice = price;
        this.accommodationUrl = url;
    }
}
