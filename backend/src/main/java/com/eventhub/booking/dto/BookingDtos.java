package com.eventhub.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eventhub.booking.Booking;
import com.eventhub.booking.BookingStatus;
import com.eventhub.event.Event;
import com.eventhub.event.EventStatus;
import com.eventhub.event.dto.CategoryRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BookingDtos {

    private BookingDtos() {
    }

    /** Max tickets in one booking (across all ticket types). */
    public static final int MAX_TICKETS_PER_BOOKING = 10;

    public record CreateBookingRequest(
            @NotNull UUID eventId,
            @NotEmpty @Size(max = 5) List<@Valid @NotNull ItemRequest> items) {
    }

    public record ItemRequest(
            @NotNull UUID ticketTypeId,
            @NotNull @Min(value = 1, message = "must be at least 1") @Max(MAX_TICKETS_PER_BOOKING) Integer quantity) {
    }

    public record EventInfo(UUID id, String title, CategoryRef category, String venueName, String address, String city,
            Instant startsAt, Instant endsAt, EventStatus status, String organizerName) {

        static EventInfo from(Event e) {
            return new EventInfo(e.getId(), e.getTitle(), CategoryRef.from(e.getCategory()), e.getVenueName(),
                    e.getAddress(), e.getCity(), e.getStartsAt(), e.getEndsAt(), e.getStatus(),
                    e.getOrganizer().getDisplayName());
        }
    }

    public record ItemResponse(UUID ticketTypeId, String name, int quantity, BigDecimal unitPrice) {
    }

    /**
     * An entry pass. {@code qr} is the QR payload (a random token; no personal data) and is
     * only included for valid passes, and only ever returned to the booking's owner.
     */
    public record PassResponse(UUID id, String code, String qr, String ticketTypeName, String status,
            Instant checkedInAt) {
    }

    public record BookingResponse(
            UUID id,
            String reference,
            BookingStatus status,
            int ticketCount,
            BigDecimal totalAmount,
            String currency,
            Instant createdAt,
            Instant cancelledAt,
            String cancellationReason,
            EventInfo event,
            List<ItemResponse> items,
            List<PassResponse> tickets,
            /** Latest moment the attendee may cancel. */
            Instant cancellationDeadline,
            boolean canCancel) {

        public static BookingResponse from(Booking b, Instant now) {
            Event e = b.getEvent();
            boolean canCancel = b.getStatus() == BookingStatus.CONFIRMED && now.isBefore(e.cancellationDeadline());
            return new BookingResponse(b.getId(), b.getReference(), b.getStatus(), b.getTicketCount(),
                    b.getTotalAmount(), b.getCurrency(), b.getCreatedAt(), b.getCancelledAt(),
                    b.getCancellationReason(), EventInfo.from(e),
                    b.getItems().stream().map(i -> new ItemResponse(i.getTicketType().getId(),
                            i.getTicketType().getName(), i.getQuantity(), i.getUnitPrice())).toList(),
                    b.getTickets().stream().map(t -> new PassResponse(t.getId(), t.displayCode(),
                            t.getStatus() == com.eventhub.checkin.Ticket.Status.VALID ? t.qrPayload() : null,
                            t.getTicketType().getName(), t.isCheckedIn() ? "CHECKED_IN" : t.getStatus().name(),
                            t.getCheckedInAt())).toList(),
                    e.cancellationDeadline(), canCancel && b.getTickets().stream().noneMatch(com.eventhub.checkin.Ticket::isCheckedIn));
        }
    }

    /** Compact row for "My bookings". */
    public record BookingSummary(UUID id, String reference, BookingStatus status, int ticketCount,
            BigDecimal totalAmount, String currency, Instant createdAt, EventInfo event) {

        public static BookingSummary from(Booking b) {
            return new BookingSummary(b.getId(), b.getReference(), b.getStatus(), b.getTicketCount(),
                    b.getTotalAmount(), b.getCurrency(), b.getCreatedAt(), EventInfo.from(b.getEvent()));
        }
    }
}
