package com.eventhub.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

import com.eventhub.category.Category;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.ticket.TicketType;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private OrganizerProfile organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 5000)
    private String description;

    @Column(name = "venue_name", nullable = false, length = 150)
    private String venueName;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "banner_key", length = 300)
    private String bannerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.DRAFT;

    /** Attendees may cancel bookings until this many hours before the start (0 = until it starts). */
    @Column(name = "cancellation_cutoff_hours", nullable = false)
    private int cancellationCutoffHours = 24;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Admin who last approved, rejected or unpublished the event. */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /** Shown to the organizer when an event is rejected or unpublished. */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("price ASC, name ASC")
    private List<TicketType> ticketTypes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {
    }

    public Event(OrganizerProfile organizer, Category category, String title, String description,
            String venueName, String address, String city, Instant startsAt, Instant endsAt) {
        this.organizer = organizer;
        this.category = category;
        this.title = title;
        this.description = description;
        this.venueName = venueName;
        this.address = address;
        this.city = city;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public TicketType addTicketType(TicketType ticketType) {
        ticketTypes.add(ticketType);
        return ticketType;
    }

    public void removeTicketType(TicketType ticketType) {
        ticketTypes.remove(ticketType);
    }

    public boolean isPubliclyVisible() {
        return status == EventStatus.PUBLISHED;
    }

    /** Organizers may change details and tickets only before review or after a rejection. */
    public boolean isEditable() {
        return status == EventStatus.DRAFT || status == EventStatus.REJECTED;
    }

    public boolean isOwnedBy(UUID organizerProfileId) {
        return organizer.getId().equals(organizerProfileId);
    }

    // ------------------------------------------------------------ moderation transitions

    /** Organizer submits for review. Clears any previous rejection. */
    public void submitForApproval(Instant now) {
        requireStatus(EventStatus.DRAFT, EventStatus.REJECTED);
        status = EventStatus.PENDING_APPROVAL;
        submittedAt = now;
        rejectionReason = null;
    }

    public void approve(UUID adminId, Instant now) {
        requireStatus(EventStatus.PENDING_APPROVAL);
        status = EventStatus.PUBLISHED;
        reviewedBy = adminId;
        reviewedAt = now;
        rejectionReason = null;
    }

    public void reject(UUID adminId, Instant now, String reason) {
        requireStatus(EventStatus.PENDING_APPROVAL);
        status = EventStatus.REJECTED;
        reviewedBy = adminId;
        reviewedAt = now;
        rejectionReason = reason;
    }

    /** Admin takedown of a live event; the organizer can fix it and resubmit. */
    public void unpublish(UUID adminId, Instant now, String reason) {
        requireStatus(EventStatus.PUBLISHED);
        status = EventStatus.REJECTED;
        reviewedBy = adminId;
        reviewedAt = now;
        rejectionReason = reason;
    }

    private void requireStatus(EventStatus... allowed) {
        for (EventStatus s : allowed) {
            if (status == s) return;
        }
        throw new IllegalStateException("Event is " + status);
    }

    /** Latest moment an attendee may still cancel a booking for this event. */
    public Instant cancellationDeadline() {
        return startsAt.minus(java.time.Duration.ofHours(cancellationCutoffHours));
    }

    public void setCancellationCutoffHours(int hours) { this.cancellationCutoffHours = hours; }
    public int getCancellationCutoffHours() { return cancellationCutoffHours; }

    public void updateDetails(Category category, String title, String description, String venueName,
            String address, String city, Instant startsAt, Instant endsAt) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.venueName = venueName;
        this.address = address;
        this.city = city;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public UUID getId() { return id; }
    public OrganizerProfile getOrganizer() { return organizer; }
    public Category getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getVenueName() { return venueName; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getBannerKey() { return bannerKey; }
    public EventStatus getStatus() { return status; }
    public List<TicketType> getTicketTypes() { return List.copyOf(ticketTypes); }
    public Instant getCreatedAt() { return createdAt; }

    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public String getRejectionReason() { return rejectionReason; }

    public void setStatus(EventStatus status) { this.status = status; }
}
