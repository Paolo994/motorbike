package com.motorbike.user;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(length = 500)
    private String bio;

    @Column(length = 80)
    private String city;

    @Column(name = "bike_model", length = 80)
    private String bikeModel;

    @Column(name = "fuel_consumption_km_per_l", precision = 5, scale = 2)
    private BigDecimal fuelConsumptionKmPerL;

    @Column(name = "tank_capacity_l", precision = 4, scale = 1)
    private BigDecimal tankCapacityL;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Foto profilo approvata in uso (vedi modulo photo); null = avatar con iniziale. */
    @Column(name = "photo_id")
    private Long photoId;

    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    /** Nome da mostrare: il display name se presente, altrimenti lo username. */
    public String getShownName() {
        return displayName != null && !displayName.isBlank() ? displayName : username;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public String getCity() { return city; }
    public String getBikeModel() { return bikeModel; }
    public BigDecimal getFuelConsumptionKmPerL() { return fuelConsumptionKmPerL; }
    public BigDecimal getTankCapacityL() { return tankCapacityL; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getPhotoId() { return photoId; }
    public boolean isAdmin() { return role == Role.ADMIN; }

    public void setRole(Role role) { this.role = role; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setBio(String bio) { this.bio = bio; }
    public void setCity(String city) { this.city = city; }
    public void setBikeModel(String bikeModel) { this.bikeModel = bikeModel; }
    public void setFuelConsumptionKmPerL(BigDecimal v) { this.fuelConsumptionKmPerL = v; }
    public void setTankCapacityL(BigDecimal v) { this.tankCapacityL = v; }
    public void setPhotoId(Long photoId) { this.photoId = photoId; }
}
