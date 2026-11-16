package com.eventhub.event.organizer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.booking.BookingService;
import com.eventhub.category.Category;
import com.eventhub.category.CategoryRepository;
import com.eventhub.common.exception.ApiException;
import com.eventhub.common.response.PageResponse;
import com.eventhub.event.Event;
import com.eventhub.event.EventRepository;
import com.eventhub.event.EventStatus;
import com.eventhub.event.dto.CategoryRef;
import com.eventhub.event.organizer.dto.EventRequest;
import com.eventhub.event.organizer.dto.OrganizerEventResponse;
import com.eventhub.event.organizer.dto.OrganizerEventSummary;
import com.eventhub.event.organizer.dto.TicketTypeRequest;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.organizer.OrganizerProfileService;
import com.eventhub.security.CurrentUser;
import com.eventhub.ticket.TicketType;

/**
 * Organizer-side event management. Every operation on an existing event goes
 * through {@link #loadAccessible}, which enforces ownership: an organizer can
 * only see and change events of their own organizer profile. Other
 * organizers' events answer 404 so their existence is not revealed. Admins
 * may access any event (support access).
 *
 * <p>Lifecycle: DRAFT/REJECTED (editable) → submit → PENDING_APPROVAL →
 * (admin, Phase 5) → PUBLISHED. Pending events can be withdrawn back to DRAFT.
 * Drafts can be deleted; anything not yet finished can be cancelled.
 */
@Service
public class OrganizerEventService {

    static final Duration MAX_EVENT_DURATION = Duration.ofDays(30);
    static final String CURRENCY = "INR";
    static final int MAX_PAGE_SIZE = 50;

    private final EventRepository events;
    private final CategoryRepository categories;
    private final OrganizerProfileService profiles;
    private final BookingService bookings;
    private final Clock clock;

    public OrganizerEventService(EventRepository events, CategoryRepository categories,
            OrganizerProfileService profiles, BookingService bookings, Clock clock) {
        this.events = events;
        this.categories = categories;
        this.profiles = profiles;
        this.bookings = bookings;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<OrganizerEventSummary> listOwn(CurrentUser caller, EventStatus status, int page, int size) {
        OrganizerProfile profile = profiles.requireForUser(caller.id());
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("startsAt"), Sort.Order.asc("id")));
        Page<Event> result = status == null
                ? events.findByOrganizerId(profile.getId(), pageable)
                : events.findByOrganizerIdAndStatus(profile.getId(), status, pageable);

        Map<UUID, EventRepository.TicketStats> stats = result.isEmpty() ? Map.of()
                : events.findTicketStats(result.map(Event::getId).toList()).stream()
                        .collect(Collectors.toMap(EventRepository.TicketStats::getEventId, Function.identity()));

        return PageResponse.from(result.map(e -> {
            EventRepository.TicketStats s = stats.get(e.getId());
            return new OrganizerEventSummary(e.getId(), e.getTitle(), CategoryRef.from(e.getCategory()), e.getCity(),
                    e.getStartsAt(), e.getEndsAt(), e.getStatus(),
                    s == null ? 0 : s.getTicketTypes(), s == null ? 0 : s.getCapacity());
        }));
    }

    @Transactional(readOnly = true)
    public OrganizerEventResponse get(CurrentUser caller, UUID eventId) {
        return OrganizerEventResponse.from(loadAccessible(caller, eventId));
    }

    public record Stats(long total, long draft, long pendingApproval, long published, long rejected,
            long cancelled, long completed, long upcoming) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Transactional(readOnly = true)
    public Stats stats(UUID organizerProfileId) {
        Map<EventStatus, Long> by = new EnumMap<>(EventStatus.class);
        events.countByStatus(organizerProfileId).forEach(c -> by.put(c.getStatus(), c.getCount()));
        long total = by.values().stream().mapToLong(Long::longValue).sum();
        return new Stats(total, by.getOrDefault(EventStatus.DRAFT, 0L),
                by.getOrDefault(EventStatus.PENDING_APPROVAL, 0L), by.getOrDefault(EventStatus.PUBLISHED, 0L),
                by.getOrDefault(EventStatus.REJECTED, 0L), by.getOrDefault(EventStatus.CANCELLED, 0L),
                by.getOrDefault(EventStatus.COMPLETED, 0L),
                events.countUpcomingPublished(organizerProfileId, clock.instant()));
    }

    // ---------------------------------------------------------------- event details

    @Transactional
    public OrganizerEventResponse create(CurrentUser caller, EventRequest req) {
        OrganizerProfile profile = profiles.requireForUser(caller.id());
        validateTimes(req.startsAt(), req.endsAt());
        Event event = new Event(profile, category(req.category()), req.title().trim(), req.description().trim(),
                req.venueName().trim(), req.address().trim(), req.city().trim(), req.startsAt(), req.endsAt());
        event.setCancellationCutoffHours(req.cancellationCutoffHours() == null ? 24 : req.cancellationCutoffHours());
        return OrganizerEventResponse.from(events.saveAndFlush(event));
    }

    @Transactional
    public OrganizerEventResponse update(CurrentUser caller, UUID eventId, EventRequest req) {
        Event event = loadAccessible(caller, eventId);
        requireEditable(event);
        validateTimes(req.startsAt(), req.endsAt());
        event.updateDetails(category(req.category()), req.title().trim(), req.description().trim(),
                req.venueName().trim(), req.address().trim(), req.city().trim(), req.startsAt(), req.endsAt());
        if (req.cancellationCutoffHours() != null) event.setCancellationCutoffHours(req.cancellationCutoffHours());
        return OrganizerEventResponse.from(events.saveAndFlush(event));
    }

    // ---------------------------------------------------------------- lifecycle

    @Transactional
    public OrganizerEventResponse submit(CurrentUser caller, UUID eventId) {
        Event event = loadAccessible(caller, eventId);
        if (!event.isEditable()) {
            throw ApiException.conflict("Only draft or rejected events can be submitted for approval");
        }
        if (!event.getStartsAt().isAfter(clock.instant())) {
            throw ApiException.badRequest("The event start time has passed; update the dates before submitting");
        }
        if (event.getTicketTypes().stream().noneMatch(t -> t.getStatus() == TicketType.Status.ACTIVE)) {
            throw ApiException.badRequest("Add at least one ticket type before submitting");
        }
        for (TicketType t : event.getTicketTypes()) {
            if (t.getSalesEndAt() != null && t.getSalesEndAt().isAfter(event.getEndsAt())) {
                throw ApiException.badRequest("Ticket sales for '" + t.getName() + "' end after the event ends");
            }
        }
        event.submitForApproval(clock.instant());
        return OrganizerEventResponse.from(event);
    }

    @Transactional
    public OrganizerEventResponse withdraw(CurrentUser caller, UUID eventId) {
        Event event = loadAccessible(caller, eventId);
        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Only events pending approval can be withdrawn");
        }
        event.setStatus(EventStatus.DRAFT);
        return OrganizerEventResponse.from(event);
    }

    @Transactional
    public OrganizerEventResponse cancel(CurrentUser caller, UUID eventId) {
        Event event = loadAccessible(caller, eventId);
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw ApiException.conflict("This event is already " + event.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (!event.getEndsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("This event has already ended");
        }
        event.setStatus(EventStatus.CANCELLED);
        bookings.cancelAllForEvent(event.getId(), "Event cancelled by the organizer");
        return OrganizerEventResponse.from(event);
    }

    @Transactional
    public void deleteDraft(CurrentUser caller, UUID eventId) {
        Event event = loadAccessible(caller, eventId);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw ApiException.conflict("Only drafts can be deleted; cancel the event instead");
        }
        events.delete(event);
    }

    // ---------------------------------------------------------------- ticket types

    @Transactional
    public OrganizerEventResponse addTicketType(CurrentUser caller, UUID eventId, TicketTypeRequest req) {
        Event event = loadAccessible(caller, eventId);
        requireEditable(event);
        String name = req.name().trim();
        requireUniqueName(event, name, null);
        validateSalesWindow(event, req);
        event.addTicketType(new TicketType(event, name, req.price(), CURRENCY, req.totalQuantity(),
                req.salesStartAt(), req.salesEndAt()));
        return OrganizerEventResponse.from(events.saveAndFlush(event));
    }

    @Transactional
    public OrganizerEventResponse updateTicketType(CurrentUser caller, UUID eventId, UUID ticketTypeId,
            TicketTypeRequest req) {
        Event event = loadAccessible(caller, eventId);
        requireEditable(event);
        TicketType ticket = ticketType(event, ticketTypeId);
        if (req.totalQuantity() < ticket.getSoldQuantity()) {
            throw ApiException.conflict(ticket.getSoldQuantity() + " '" + ticket.getName()
                    + "' tickets are already booked; the quantity can't go below that");
        }
        String name = req.name().trim();
        requireUniqueName(event, name, ticketTypeId);
        validateSalesWindow(event, req);
        ticket.update(name, req.price(), CURRENCY, req.totalQuantity(), req.salesStartAt(), req.salesEndAt());
        return OrganizerEventResponse.from(events.saveAndFlush(event));
    }

    @Transactional
    public OrganizerEventResponse deleteTicketType(CurrentUser caller, UUID eventId, UUID ticketTypeId) {
        Event event = loadAccessible(caller, eventId);
        requireEditable(event);
        TicketType ticket = ticketType(event, ticketTypeId);
        if (ticket.getSoldQuantity() > 0) {
            throw ApiException.conflict("'" + ticket.getName() + "' already has bookings and can't be deleted");
        }
        event.removeTicketType(ticket);
        return OrganizerEventResponse.from(events.saveAndFlush(event));
    }

    // ---------------------------------------------------------------- rules

    /**
     * Ownership check used by every operation on an existing event. Not found
     * and not yours look identical (404) to the caller.
     */
    public Event loadAccessible(CurrentUser caller, UUID eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Event not found"));
        if (caller.hasRole("ADMIN")) {
            return event;
        }
        UUID ownProfileId = profiles.findForUser(caller.id()).map(OrganizerProfile::getId).orElse(null);
        if (ownProfileId == null || !event.isOwnedBy(ownProfileId)) {
            throw ApiException.notFound("Event not found");
        }
        return event;
    }

    private static void requireEditable(Event event) {
        if (!event.isEditable()) {
            throw ApiException.conflict(switch (event.getStatus()) {
                case PENDING_APPROVAL -> "This event is awaiting approval. Withdraw it to make changes.";
                case PUBLISHED -> "Published events can't be edited.";
                default -> "This event can no longer be edited.";
            });
        }
    }

    private void validateTimes(Instant startsAt, Instant endsAt) {
        if (!startsAt.isAfter(clock.instant())) {
            throw ApiException.badRequest("The event must start in the future");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw ApiException.badRequest("The event must end after it starts");
        }
        if (Duration.between(startsAt, endsAt).compareTo(MAX_EVENT_DURATION) > 0) {
            throw ApiException.badRequest("Events can last at most 30 days");
        }
    }

    private static void validateSalesWindow(Event event, TicketTypeRequest req) {
        Instant start = req.salesStartAt();
        Instant end = req.salesEndAt();
        if (start != null && end != null && !end.isAfter(start)) {
            throw ApiException.badRequest("Ticket sales must end after they start");
        }
        if (end != null && end.isAfter(event.getEndsAt())) {
            throw ApiException.badRequest("Ticket sales can't continue after the event ends");
        }
        if (start != null && !start.isBefore(event.getEndsAt())) {
            throw ApiException.badRequest("Ticket sales must start before the event ends");
        }
    }

    private static void requireUniqueName(Event event, String name, UUID exceptId) {
        boolean clash = event.getTicketTypes().stream()
                .anyMatch(t -> !t.getId().equals(exceptId) && t.getName().equalsIgnoreCase(name));
        if (clash) {
            throw ApiException.conflict("A ticket type named '" + name + "' already exists for this event");
        }
    }

    private static TicketType ticketType(Event event, UUID ticketTypeId) {
        return event.getTicketTypes().stream().filter(t -> t.getId().equals(ticketTypeId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Ticket type not found"));
    }

    private Category category(String slug) {
        return categories.findBySlug(slug.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.badRequest("Unknown category '" + slug + "'"));
    }
}
