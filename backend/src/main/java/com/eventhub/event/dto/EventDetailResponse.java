package com.eventhub.event.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventDetailResponse(
        UUID id,
        String title,
        String description,
        CategoryRef category,
        String venueName,
        String address,
        String city,
        Instant startsAt,
        Instant endsAt,
        boolean ended,
        OrganizerRef organizer,
        List<TicketTypeResponse> ticketTypes,
        PriceInfo price,
        /** Attendees may cancel until this many hours before the start. */
        int cancellationCutoffHours) {

    public record OrganizerRef(UUID id, String displayName, String description, boolean verified) {
    }

    /**
     * @param available tickets still available
     * @param onSale    whether tickets can be booked right now (sales window open, not sold out)
     * @param soldOut   no tickets left
     */
    public record TicketTypeResponse(
            UUID id,
            String name,
            BigDecimal price,
            String currency,
            int totalQuantity,
            int available,
            Instant salesStartAt,
            Instant salesEndAt,
            boolean onSale,
            boolean soldOut) {
    }
}
