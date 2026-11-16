package com.eventhub.checkin;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

import com.eventhub.booking.Booking;
import com.eventhub.event.Event;
import com.eventhub.ticket.TicketType;

/** An entry pass for one person. Its token (QR) and code (manual entry) carry no personal data. */
@Entity
@Table(name = "tickets")
public class Ticket {

    public enum Status { VALID, CANCELLED }

    /** Prefix of the QR payload, e.g. "EH1.3f9a…". Versioned so the format can evolve. */
    public static final String QR_PREFIX = "EH1.";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_type_id", nullable = false)
    private TicketType ticketType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.VALID;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Ticket() {
    }

    public Ticket(Booking booking, TicketType ticketType, Event event, String token, String code) {
        this.booking = booking;
        this.ticketType = ticketType;
        this.event = event;
        this.token = token;
        this.code = code;
    }

    public void cancel() { status = Status.CANCELLED; }
    public void markCheckedIn(Instant at) { checkedInAt = at; }
    public boolean isCheckedIn() { return checkedInAt != null; }

    public String qrPayload() { return QR_PREFIX + token; }

    /** "7K3M9QX2" -> "7K3M-9QX2" for display. */
    public String displayCode() {
        return code.length() == 8 ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public TicketType getTicketType() { return ticketType; }
    public Event getEvent() { return event; }
    public String getToken() { return token; }
    public String getCode() { return code; }
    public Status getStatus() { return status; }
    public Instant getCheckedInAt() { return checkedInAt; }
}
