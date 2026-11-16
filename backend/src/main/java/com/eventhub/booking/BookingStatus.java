package com.eventhub.booking;

public enum BookingStatus {
    /** Reserved, awaiting payment (paid tickets, Phase 9). */
    PENDING,
    CONFIRMED,
    CANCELLED,
    /** Reservation timed out before payment (Phase 9). */
    EXPIRED,
    REFUNDED
}
