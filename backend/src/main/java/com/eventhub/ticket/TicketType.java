package com.eventhub.ticket;

import java.math.BigDecimal;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.eventhub.event.Event;

@Entity
@Table(name = "ticket_types")
public class TicketType {

    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    /** Tickets sold so far. Only changed while this row is locked (see BookingService). */
    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Column(name = "sales_start_at")
    private Instant salesStartAt;

    @Column(name = "sales_end_at")
    private Instant salesEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TicketType() {
    }

    public TicketType(Event event, String name, BigDecimal price, String currency, int totalQuantity,
            Instant salesStartAt, Instant salesEndAt) {
        this.event = event;
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.totalQuantity = totalQuantity;
        this.salesStartAt = salesStartAt;
        this.salesEndAt = salesEndAt;
    }

    public void update(String name, BigDecimal price, String currency, int totalQuantity,
            Instant salesStartAt, Instant salesEndAt) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.totalQuantity = totalQuantity;
        this.salesStartAt = salesStartAt;
        this.salesEndAt = salesEndAt;
    }

    public int getAvailable() {
        return Math.max(0, totalQuantity - soldQuantity);
    }

    public boolean isFree() {
        return price.signum() == 0;
    }

    /** Takes tickets from inventory. Caller must hold the row lock and check availability first. */
    public void reserve(int quantity) {
        if (quantity <= 0 || quantity > getAvailable()) {
            throw new IllegalStateException("Cannot reserve " + quantity + " of " + getAvailable());
        }
        soldQuantity += quantity;
    }

    /** Returns tickets to inventory (cancellation). Caller must hold the row lock. */
    public void release(int quantity) {
        soldQuantity = Math.max(0, soldQuantity - quantity);
    }

    /** Whether tickets can be bought right now, ignoring inventory. */
    public boolean isOnSale(Instant now) {
        return status == Status.ACTIVE
                && (salesStartAt == null || !now.isBefore(salesStartAt))
                && (salesEndAt == null || now.isBefore(salesEndAt));
    }

    public UUID getId() { return id; }
    public Event getEvent() { return event; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getSoldQuantity() { return soldQuantity; }
    public Instant getSalesStartAt() { return salesStartAt; }
    public Instant getSalesEndAt() { return salesEndAt; }
    public Status getStatus() { return status; }

    public void setStatus(Status status) { this.status = status; }
}
