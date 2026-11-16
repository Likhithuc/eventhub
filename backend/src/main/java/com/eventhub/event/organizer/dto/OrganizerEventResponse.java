package com.eventhub.event.organizer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.eventhub.event.Event;
import com.eventhub.event.EventStatus;
import com.eventhub.event.dto.CategoryRef;
import com.eventhub.ticket.TicketType;

/** Full organizer view of one event, including non-public statuses and every ticket type. */
public record OrganizerEventResponse(
        UUID id,
        String title,
        String description,
        CategoryRef category,
        String venueName,
        String address,
        String city,
        Instant startsAt,
        Instant endsAt,
        EventStatus status,
        boolean editable,
        List<TicketTypeView> ticketTypes,
        Instant createdAt,
        Instant submittedAt,
        Instant reviewedAt,
        /** Why an admin rejected or unpublished the event; null otherwise. */
        String rejectionReason,
        int cancellationCutoffHours) {

    public record TicketTypeView(
            UUID id,
            String name,
            BigDecimal price,
            String currency,
            int totalQuantity,
            Instant salesStartAt,
            Instant salesEndAt,
            TicketType.Status status,
            int soldQuantity) {

        public static TicketTypeView from(TicketType t) {
            return new TicketTypeView(t.getId(), t.getName(), t.getPrice(), t.getCurrency(), t.getTotalQuantity(),
                    t.getSalesStartAt(), t.getSalesEndAt(), t.getStatus(), t.getSoldQuantity());
        }
    }

    public static OrganizerEventResponse from(Event e) {
        List<TicketTypeView> tickets = e.getTicketTypes().stream()
                .sorted(Comparator.comparing(TicketType::getPrice).thenComparing(TicketType::getName))
                .map(TicketTypeView::from)
                .toList();
        return new OrganizerEventResponse(e.getId(), e.getTitle(), e.getDescription(), CategoryRef.from(e.getCategory()),
                e.getVenueName(), e.getAddress(), e.getCity(), e.getStartsAt(), e.getEndsAt(), e.getStatus(),
                e.isEditable(), tickets, e.getCreatedAt(), e.getSubmittedAt(), e.getReviewedAt(),
                e.getStatus() == EventStatus.REJECTED ? e.getRejectionReason() : null, e.getCancellationCutoffHours());
    }
}
