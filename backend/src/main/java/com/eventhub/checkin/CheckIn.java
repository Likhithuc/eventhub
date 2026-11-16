package com.eventhub.checkin;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Authoritative check-in record. UNIQUE(ticket_id) guarantees a ticket is admitted at most once. */
@Entity
@Table(name = "check_ins")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "checked_in_by")
    private UUID checkedInBy;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    protected CheckIn() {
    }

    public CheckIn(Ticket ticket, UUID checkedInBy, Instant at) {
        this.ticket = ticket;
        this.eventId = ticket.getEvent().getId();
        this.checkedInBy = checkedInBy;
        this.checkedInAt = at;
    }

    public UUID getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public UUID getCheckedInBy() { return checkedInBy; }
    public Instant getCheckedInAt() { return checkedInAt; }
}
