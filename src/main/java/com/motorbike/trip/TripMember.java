package com.motorbike.trip;

import java.time.Instant;

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

import com.motorbike.trip.TripEnums.TripRole;
import com.motorbike.user.User;

@Entity
@Table(name = "trip_members")
public class TripMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TripMember() {
    }

    TripMember(Trip trip, User user, TripRole role) {
        this.trip = trip;
        this.user = user;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public User getUser() { return user; }
    public TripRole getRole() { return role; }

    void setRole(TripRole role) { this.role = role; }
}
