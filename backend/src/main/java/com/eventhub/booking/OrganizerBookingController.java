package com.eventhub.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.common.response.PageResponse;
import com.eventhub.event.Event;
import com.eventhub.event.organizer.OrganizerEventService;
import com.eventhub.security.CurrentUser;
import com.eventhub.ticket.TicketType;

/** Bookings and attendee list for an organizer's own event (ownership checked by OrganizerEventService). */
@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}/bookings")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
public class OrganizerBookingController {

    private final OrganizerEventService organizerEvents;
    private final BookingRepository bookings;

    public OrganizerBookingController(OrganizerEventService organizerEvents, BookingRepository bookings) {
        this.organizerEvents = organizerEvents;
        this.bookings = bookings;
    }

    public record TicketSales(UUID id, String name, BigDecimal price, int totalQuantity, int sold) {
    }

    public record Summary(long confirmedBookings, long ticketsSold, long capacity, List<TicketSales> ticketTypes) {
    }

    public record AttendeeRow(UUID id, String reference, BookingStatus status, String attendeeName, String attendeeEmail,
            int ticketCount, long checkedIn, List<String> items, Instant createdAt, Instant cancelledAt) {
    }

    public record Response(Summary summary, PageResponse<AttendeeRow> bookings) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Response list(CurrentUser user, @PathVariable UUID eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Event event = organizerEvents.loadAccessible(user, eventId);

        List<TicketSales> sales = event.getTicketTypes().stream()
                .sorted(Comparator.comparing(TicketType::getPrice).thenComparing(TicketType::getName))
                .map(t -> new TicketSales(t.getId(), t.getName(), t.getPrice(), t.getTotalQuantity(), t.getSoldQuantity()))
                .toList();
        BookingRepository.EventBookingStats stats = bookings.statsForEvent(eventId);
        long capacity = sales.stream().mapToLong(TicketSales::totalQuantity).sum();
        Summary summary = new Summary(stats.getBookings(), stats.getTickets() == null ? 0 : stats.getTickets(), capacity, sales);

        var result = bookings.findForEvent(eventId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)))
                .map(b -> new AttendeeRow(b.getId(), b.getReference(), b.getStatus(), b.getUser().getName(),
                        b.getUser().getEmail(), b.getTicketCount(),
                        b.getTickets().stream().filter(com.eventhub.checkin.Ticket::isCheckedIn).count(),
                        b.getItems().stream().map(i -> i.getQuantity() + " × " + i.getTicketType().getName()).toList(),
                        b.getCreatedAt(), b.getCancelledAt()));
        return new Response(summary, PageResponse.from(result));
    }
}
