package com.eventhub.booking;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.booking.dto.BookingDtos.BookingResponse;
import com.eventhub.booking.dto.BookingDtos.BookingSummary;
import com.eventhub.booking.dto.BookingDtos.CreateBookingRequest;
import com.eventhub.booking.dto.BookingDtos.ItemRequest;
import com.eventhub.checkin.TicketIssuer;
import com.eventhub.common.exception.ApiException;
import com.eventhub.common.response.PageResponse;
import com.eventhub.event.Event;
import com.eventhub.event.EventRepository;
import com.eventhub.event.EventStatus;
import com.eventhub.security.CurrentUser;
import com.eventhub.ticket.TicketType;
import com.eventhub.ticket.TicketTypeRepository;
import com.eventhub.user.User;
import com.eventhub.user.UserService;

import jakarta.persistence.EntityManager;

import org.springframework.context.ApplicationEventPublisher;

import static com.eventhub.booking.dto.BookingDtos.MAX_TICKETS_PER_BOOKING;

/**
 * Booking and inventory.
 *
 * <p><b>No overselling.</b> A booking runs in one transaction that:
 * <ol>
 *   <li>locks the requested ticket-type rows ({@code SELECT ... FOR UPDATE}, in id order),</li>
 *   <li>checks availability against the locked, authoritative counts,</li>
 *   <li>increments {@code sold_quantity} and writes the booking, then commits.</li>
 * </ol>
 * Concurrent bookings for the same tickets therefore run one after another, and the
 * {@code sold_quantity <= total_quantity} CHECK constraint backs this up in the database.
 *
 * <p><b>No duplicates on retry.</b> Clients send an {@code Idempotency-Key}. A repeat of
 * the same key returns the original booking. The check is repeated after taking the
 * locks, so two simultaneous duplicates also produce a single booking.
 *
 * <p>Free tickets are confirmed immediately. Paid tickets need online payments (Phase 9).
 */
@Service
public class BookingService {

    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookings;
    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final UserService users;
    private final Clock clock;
    private final EntityManager em;
    private final TicketIssuer issuer;
    private final ApplicationEventPublisher publisher;

    public BookingService(BookingRepository bookings, EventRepository events, TicketTypeRepository ticketTypes,
            UserService users, Clock clock, EntityManager em, TicketIssuer issuer, ApplicationEventPublisher publisher) {
        this.bookings = bookings;
        this.events = events;
        this.ticketTypes = ticketTypes;
        this.users = users;
        this.clock = clock;
        this.em = em;
        this.issuer = issuer;
        this.publisher = publisher;
    }

    /** Result of a create: the booking, and whether it was newly created or replayed from an idempotency key. */
    public record CreateResult(BookingResponse booking, boolean created) {
    }

    @Transactional
    public CreateResult create(UUID userId, CreateBookingRequest req, String idempotencyKey) {
        validateShape(req);
        String key = normalizeKey(idempotencyKey);
        Instant now = clock.instant();

        // Fast path for retries (no locks needed).
        if (key != null) {
            var existing = bookings.findByUserIdAndIdempotencyKey(userId, key);
            if (existing.isPresent()) return replay(existing.get(), req, now);
        }

        User user = users.getActiveUser(userId);
        Event event = events.findById(req.eventId())
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Event not found"));
        if (!event.getStartsAt().isAfter(now)) {
            throw ApiException.conflict("Bookings have closed for this event");
        }

        Map<UUID, Integer> wanted = req.items().stream()
                .collect(Collectors.toMap(ItemRequest::ticketTypeId, ItemRequest::quantity));

        // Serialisation point: lock the ticket rows. Everything below sees committed, current counts.
        List<TicketType> locked = lock(ticketTypes.lockForBooking(wanted.keySet(), event.getId()));
        if (locked.size() != wanted.size()) {
            throw ApiException.badRequest("One or more ticket types don't belong to this event");
        }

        // A simultaneous duplicate may have committed while we waited for the lock.
        if (key != null) {
            var existing = bookings.findByUserIdAndIdempotencyKey(userId, key);
            if (existing.isPresent()) return replay(existing.get(), req, now);
        }

        for (TicketType t : locked) {
            int qty = wanted.get(t.getId());
            if (t.getStatus() != TicketType.Status.ACTIVE || !t.isOnSale(now)) {
                throw ApiException.conflict("'" + t.getName() + "' tickets are not on sale right now");
            }
            if (!t.isFree()) {
                throw ApiException.badRequest("'" + t.getName() + "' is a paid ticket. Online payment is coming soon; "
                        + "only free tickets can be booked for now.");
            }
            if (t.getAvailable() == 0) {
                throw ApiException.conflict("'" + t.getName() + "' is sold out");
            }
            if (qty > t.getAvailable()) {
                throw ApiException.conflict("Only " + t.getAvailable() + " '" + t.getName() + "' ticket"
                        + (t.getAvailable() == 1 ? " is" : "s are") + " left");
            }
        }

        Booking booking = new Booking(newReference(), user, event, BookingStatus.CONFIRMED,
                locked.getFirst().getCurrency(), key);
        for (TicketType t : locked) {
            int qty = wanted.get(t.getId());
            t.reserve(qty);
            BookingItem item = new BookingItem(booking, t, qty);
            booking.addItem(item);
            issuer.issue(booking, item);
        }
        booking = bookings.saveAndFlush(booking);
        // Email is sent only after this transaction commits (see BookingMailer).
        publisher.publishEvent(new BookingConfirmedEvent(booking.getId()));
        return new CreateResult(BookingResponse.from(booking, now), true);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(CurrentUser caller, UUID bookingId) {
        return BookingResponse.from(loadAccessible(caller, bookingId), clock.instant());
    }

    public enum Scope { UPCOMING, PAST, CANCELLED }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummary> listMine(UUID userId, Scope scope, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50));
        Instant now = clock.instant();
        Page<Booking> result = switch (scope) {
            case UPCOMING -> bookings.findUpcoming(userId, now, pageable);
            case PAST -> bookings.findPast(userId, now, pageable);
            case CANCELLED -> bookings.findCancelled(userId, pageable);
        };
        return PageResponse.from(result.map(BookingSummary::from));
    }

    /** Attendee cancels their own booking, within the event's cancellation window. Tickets return to inventory. */
    @Transactional
    public BookingResponse cancel(CurrentUser caller, UUID bookingId) {
        Booking booking = loadAccessible(caller, bookingId);
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict("This booking is already " + booking.getStatus().name().toLowerCase());
        }
        if (booking.getTickets().stream().anyMatch(com.eventhub.checkin.Ticket::isCheckedIn)) {
            throw ApiException.conflict("Tickets from this booking have already been checked in");
        }
        Event event = booking.getEvent();
        if (!now.isBefore(event.cancellationDeadline())) {
            throw ApiException.conflict(event.getCancellationCutoffHours() == 0
                    ? "This event has started; the booking can no longer be cancelled"
                    : "Cancellations closed " + event.getCancellationCutoffHours() + " hours before the event starts");
        }
        releaseInventory(booking);
        booking.cancel(now, "Cancelled by attendee");
        return BookingResponse.from(booking, now);
    }

    /** Organizer cancelled the event: cancel every active booking and return the tickets. */
    @Transactional
    public int cancelAllForEvent(UUID eventId, String reason) {
        Instant now = clock.instant();
        Map<UUID, TicketType> lockedById = lock(ticketTypes.lockAllForEvent(eventId)).stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));
        List<Booking> active = bookings.findActiveForEvent(eventId);
        for (Booking b : active) {
            b.getItems().forEach(i -> lockedById.get(i.getTicketType().getId()).release(i.getQuantity()));
            b.cancel(now, reason);
        }
        return active.size();
    }

    // ------------------------------------------------------------------ helpers

    /** Owner (or admin) only. Someone else's booking looks like a missing one. */
    private Booking loadAccessible(CurrentUser caller, UUID bookingId) {
        Booking booking = bookings.findWithDetailsById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!booking.getUser().getId().equals(caller.id()) && !caller.hasRole("ADMIN")) {
            throw ApiException.notFound("Booking not found");
        }
        return booking;
    }

    /**
     * The lock query has taken SELECT ... FOR UPDATE on these rows. If an entity was
     * already loaded earlier in this transaction, Hibernate returns that instance with
     * its old state, so re-read it now that we hold the lock to get the current counts.
     */
    private List<TicketType> lock(List<TicketType> lockedRows) {
        lockedRows.forEach(em::refresh);
        return lockedRows;
    }

    private void releaseInventory(Booking booking) {
        Set<UUID> ids = booking.getItems().stream().map(i -> i.getTicketType().getId()).collect(Collectors.toSet());
        Map<UUID, TicketType> locked = lock(ticketTypes.lockForBooking(ids, booking.getEvent().getId())).stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));
        booking.getItems().forEach(i -> locked.get(i.getTicketType().getId()).release(i.getQuantity()));
    }

    private CreateResult replay(Booking existing, CreateBookingRequest req, Instant now) {
        Map<UUID, Integer> original = existing.getItems().stream()
                .collect(Collectors.toMap(i -> i.getTicketType().getId(), BookingItem::getQuantity));
        Map<UUID, Integer> requested = req.items().stream()
                .collect(Collectors.toMap(ItemRequest::ticketTypeId, ItemRequest::quantity));
        if (!existing.getEvent().getId().equals(req.eventId()) || !original.equals(requested)) {
            throw ApiException.conflict("This Idempotency-Key was already used for a different booking");
        }
        Booking full = bookings.findWithDetailsById(existing.getId()).orElseThrow();
        return new CreateResult(BookingResponse.from(full, now), false);
    }

    private static void validateShape(CreateBookingRequest req) {
        Set<UUID> seen = new HashSet<>();
        int total = 0;
        for (ItemRequest item : req.items()) {
            if (!seen.add(item.ticketTypeId())) {
                throw ApiException.badRequest("Each ticket type may appear only once");
            }
            total += item.quantity();
        }
        if (total > MAX_TICKETS_PER_BOOKING) {
            throw ApiException.badRequest("You can book at most " + MAX_TICKETS_PER_BOOKING + " tickets at a time");
        }
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();
        if (k.length() > 64 || !k.matches("[A-Za-z0-9_\\-:.]+")) {
            throw ApiException.badRequest("Idempotency-Key must be 1-64 letters, digits or -_:.");
        }
        return k;
    }

    /** e.g. EH-7K3M9QX2: unambiguous characters, ~10^12 combinations. */
    private String newReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder("EH-");
            for (int i = 0; i < 8; i++) sb.append(REF_ALPHABET.charAt(RANDOM.nextInt(REF_ALPHABET.length())));
            String ref = sb.toString();
            if (!bookings.existsByReference(ref)) return ref;
        }
        throw new IllegalStateException("Could not generate a unique booking reference");
    }
}
