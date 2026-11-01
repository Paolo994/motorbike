package com.motorbike.photo;

import java.time.Instant;

import jakarta.persistence.Basic;
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

import com.motorbike.user.User;

@Entity
@Table(name = "profile_photos")
public class ProfilePhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhotoStatus status;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    protected ProfilePhoto() {
    }

    ProfilePhoto(User user, byte[] jpeg) {
        this.user = user;
        this.status = PhotoStatus.PENDING;
        this.contentType = "image/jpeg";
        this.data = jpeg;
        this.createdAt = Instant.now();
    }

    void approve(Long adminId) {
        this.status = PhotoStatus.APPROVED;
        this.reviewedAt = Instant.now();
        this.reviewedById = adminId;
        this.rejectionReason = null;
    }

    void reject(Long adminId, String reason) {
        this.status = PhotoStatus.REJECTED;
        this.reviewedAt = Instant.now();
        this.reviewedById = adminId;
        this.rejectionReason = reason;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public PhotoStatus getStatus() { return status; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
}
