package com.eventhub.event.organizer.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or update an event's details. Times are ISO-8601 instants, e.g. 2026-12-12T14:00:00Z. */
public record EventRequest(
        @NotBlank @Size(min = 3, max = 150) String title,
        @NotBlank @Size(min = 10, max = 5000) String description,
        @NotBlank @Size(max = 60) String category,
        @NotBlank @Size(max = 150) String venueName,
        @NotBlank @Size(max = 300) String address,
        @NotBlank @Size(min = 2, max = 80) String city,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        /** Hours before the start until which attendees may cancel; default 24. */
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(720) Integer cancellationCutoffHours) {
}
