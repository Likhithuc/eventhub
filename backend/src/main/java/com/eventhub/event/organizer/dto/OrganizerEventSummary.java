package com.eventhub.event.organizer.dto;

import java.time.Instant;
import java.util.UUID;

import com.eventhub.event.EventStatus;
import com.eventhub.event.dto.CategoryRef;

/** Row in the organizer's event list. */
public record OrganizerEventSummary(
        UUID id,
        String title,
        CategoryRef category,
        String city,
        Instant startsAt,
        Instant endsAt,
        EventStatus status,
        long ticketTypes,
        long capacity) {
}
