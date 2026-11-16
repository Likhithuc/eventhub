package com.eventhub.checkin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.event.Event;
import com.eventhub.event.EventStatus;
import com.eventhub.event.organizer.OrganizerEventService;
import com.eventhub.security.CurrentUser;
import com.eventhub.user.User;
import com.eventhub.user.UserRepository;

/**
 * Door check-in. Accepts either the QR payload ("EH1.&lt;token&gt;", or a bare token)
 * or the 8-character manual code ("7K3M-9QX2", any case, with or without dashes).
 *
 * <p>Duplicate-scan protection: the ticket row is locked (SELECT ... FOR UPDATE) before
 * checking, so two simultaneous scans are processed one after the other and the second
 * sees the first check-in. UNIQUE(check_ins.ticket_id) backs this up in the database.
 */
@Service
public class CheckInService {

    /** Doors may open this long before the start ... */
    static final Duration OPENS_BEFORE_START = Duration.ofHours(24);
    /** ... and close this long after the end. */
    static final Duration CLOSES_AFTER_END = Duration.ofHours(12);

    public enum Result { CHECKED_IN, ALREADY_CHECKED_IN, CANCELLED, NOT_FOUND, TOO_EARLY, EVENT_ENDED, NOT_PUBLISHED }

    public record TicketInfo(String code, String ticketType, String attendeeName, String bookingReference) {
    }

    public record CheckInResponse(Result result, String message, TicketInfo ticket, Instant checkedInAt,
            String checkedInBy) {
    }

    public record RecentCheckIn(String code, String ticketType, String attendeeName, Instant checkedInAt) {
    }

    public record Summary(long validTickets, long checkedIn, Instant opensAt, Instant closesAt, boolean open,
            List<RecentCheckIn> recent) {
    }

    private final TicketRepository tickets;
    private final CheckInRepository checkIns;
    private final OrganizerEventService organizerEvents;
    private final UserRepository users;
    private final Clock clock;

    public CheckInService(TicketRepository tickets, CheckInRepository checkIns, OrganizerEventService organizerEvents,
            UserRepository users, Clock clock) {
        this.tickets = tickets;
        this.checkIns = checkIns;
        this.organizerEvents = organizerEvents;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public CheckInResponse checkIn(CurrentUser staff, UUID eventId, String rawCode) {
        Event event = organizerEvents.loadAccessible(staff, eventId); // ownership: 404 if not the caller's event
        Instant now = clock.instant();

        Optional<Ticket> found = lookup(rawCode);
        // A ticket for some other event is treated exactly like an unknown code.
        if (found.isEmpty() || !found.get().getEvent().getId().equals(event.getId())) {
            return new CheckInResponse(Result.NOT_FOUND, "No ticket with this code for this event", null, null, null);
        }
        Ticket ticket = found.get();
        TicketInfo info = info(ticket);

        if (event.getStatus() != EventStatus.PUBLISHED) {
            return new CheckInResponse(Result.NOT_PUBLISHED, "Check-in is only available for published events", info, null, null);
        }
        if (now.isBefore(event.getStartsAt().minus(OPENS_BEFORE_START))) {
            return new CheckInResponse(Result.TOO_EARLY, "Check-in opens 24 hours before the event starts", info, null, null);
        }
        if (now.isAfter(event.getEndsAt().plus(CLOSES_AFTER_END))) {
            return new CheckInResponse(Result.EVENT_ENDED, "Check-in has closed for this event", info, null, null);
        }
        if (ticket.getStatus() == Ticket.Status.CANCELLED) {
            return new CheckInResponse(Result.CANCELLED, "This ticket was cancelled and is not valid for entry", info, null, null);
        }
        Optional<CheckIn> existing = checkIns.findByTicketId(ticket.getId());
        if (existing.isPresent()) {
            CheckIn c = existing.get();
            return new CheckInResponse(Result.ALREADY_CHECKED_IN, "Already checked in", info, c.getCheckedInAt(),
                    nameOf(c.getCheckedInBy()));
        }

        checkIns.saveAndFlush(new CheckIn(ticket, staff.id(), now));
        ticket.markCheckedIn(now);
        return new CheckInResponse(Result.CHECKED_IN, "Welcome in!", info, now, nameOf(staff.id()));
    }

    @Transactional(readOnly = true)
    public Summary summary(CurrentUser staff, UUID eventId) {
        Event event = organizerEvents.loadAccessible(staff, eventId);
        Instant now = clock.instant();
        Instant opens = event.getStartsAt().minus(OPENS_BEFORE_START);
        Instant closes = event.getEndsAt().plus(CLOSES_AFTER_END);
        List<RecentCheckIn> recent = checkIns.findRecentForEvent(eventId, PageRequest.of(0, 15)).stream()
                .map(c -> new RecentCheckIn(c.getTicket().displayCode(), c.getTicket().getTicketType().getName(),
                        c.getTicket().getBooking().getUser().getName(), c.getCheckedInAt()))
                .toList();
        return new Summary(tickets.countValidForEvent(eventId), tickets.countCheckedInForEvent(eventId), opens, closes,
                event.getStatus() == EventStatus.PUBLISHED && !now.isBefore(opens) && !now.isAfter(closes), recent);
    }

    /** Finds and locks the ticket for a scanned/typed value. */
    private Optional<Ticket> lookup(String raw) {
        if (raw == null) return Optional.empty();
        String value = raw.trim();
        if (value.regionMatches(true, 0, Ticket.QR_PREFIX, 0, Ticket.QR_PREFIX.length())) {
            return tickets.findByTokenForUpdate(value.substring(Ticket.QR_PREFIX.length()).toLowerCase(Locale.ROOT));
        }
        String compact = value.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (compact.length() == 8) {
            return tickets.findByCodeForUpdate(compact);
        }
        if (compact.length() == 32) { // bare token pasted without the prefix
            return tickets.findByTokenForUpdate(compact.toLowerCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    private static TicketInfo info(Ticket t) {
        return new TicketInfo(t.displayCode(), t.getTicketType().getName(), t.getBooking().getUser().getName(),
                t.getBooking().getReference());
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : users.findById(userId).map(User::getName).orElse(null);
    }
}
