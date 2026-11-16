package com.eventhub.organizer;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.eventhub.user.User;

/** Public-facing organizer identity. Creating/editing profiles arrives in Phase 4. */
@Entity
@Table(name = "organizer_profiles")
public class OrganizerProfile {

    public enum VerificationStatus { PENDING, VERIFIED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 2000)
    private String description;

    @Column(name = "contact_email")
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizerProfile() {
    }

    public OrganizerProfile(User user, String displayName, String description, String contactEmail) {
        this.user = user;
        this.displayName = displayName;
        this.description = description;
        this.contactEmail = contactEmail;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getContactEmail() { return contactEmail; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }

    public void setVerificationStatus(VerificationStatus status) { this.verificationStatus = status; }

    public void update(String displayName, String description, String contactEmail) {
        this.displayName = displayName;
        this.description = description;
        this.contactEmail = contactEmail;
    }

    public java.time.Instant getCreatedAt() { return createdAt; }
}
