package com.eventhub.event.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Card-sized view of a published event for list and search results. */
public record EventSummaryResponse(
        UUID id,
        String title,
        CategoryRef category,
        String venueName,
        String city,
        Instant startsAt,
        Instant endsAt,
        String organizerName,
        PriceInfo price) {
}
