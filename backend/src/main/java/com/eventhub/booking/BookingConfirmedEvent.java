package com.eventhub.booking;

import java.util.UUID;

/** Published inside the booking transaction; handled only after it commits. */
public record BookingConfirmedEvent(UUID bookingId) {
}
