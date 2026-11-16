package com.eventhub.event;

public enum EventStatus {
    DRAFT,
    PENDING_APPROVAL,
    /** The only status visible to the public. */
    PUBLISHED,
    REJECTED,
    CANCELLED,
    COMPLETED
}
