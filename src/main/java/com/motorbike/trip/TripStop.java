package com.motorbike.trip;

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
import jakarta.persistence.Table;

import com.motorbike.trip.TripEnums.StopType;

/** Fermata intermedia di una tappa: è anche un punto di passaggio del percorso. */
@Entity
@Table(name = "trip_stops")
public class TripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id")
    private TripStage stage;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StopType type;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(length = 500)
    private String notes;

    protected TripStop() {
    }

    TripStop(TripStage stage, int position, String name, StopType type, double lat, double lon, String notes) {
        this.stage = stage;
        this.position = position;
        this.name = name;
        this.type = type;
        this.lat = lat;
        this.lon = lon;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public int getPosition() { return position; }
    public String getName() { return name; }
    public StopType getType() { return type; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getNotes() { return notes; }
}
