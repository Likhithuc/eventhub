package com.eventhub.booking;

import java.math.BigDecimal;
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
import jakarta.persistence.Table;

import com.eventhub.event.Event;
import com.eventhub.user.User;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 12)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "ticket_count", nullable = false)
    private int ticketCount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<BookingItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 50)
    @jakarta.persistence.OrderBy("createdAt ASC, code ASC")
    private List<com.eventhub.checkin.Ticket> tickets = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
    }

    public Booking(String reference, User user, Event event, BookingStatus status, String currency, String idempotencyKey) {
        this.reference = reference;
        this.user = user;
        this.event = event;
        this.status = status;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.totalAmount = BigDecimal.ZERO;
    }

    public void addItem(BookingItem item) {
        items.add(item);
        ticketCount += item.getQuantity();
        totalAmount = totalAmount.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }

    public void addTicket(com.eventhub.checkin.Ticket ticket) {
        tickets.add(ticket);
    }

    public void cancel(Instant now, String reason) {
        tickets.forEach(com.eventhub.checkin.Ticket::cancel);
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
        cancellationReason = reason;
    }

    public boolean isActive() {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.PENDING;
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public User getUser() { return user; }
    public Event getEvent() { return event; }
    public BookingStatus getStatus() { return status; }
    public int getTicketCount() { return ticketCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public List<BookingItem> getItems() { return List.copyOf(items); }
    public List<com.eventhub.checkin.Ticket> getTickets() { return List.copyOf(tickets); }
    public Instant getCreatedAt() { return createdAt; }
}
