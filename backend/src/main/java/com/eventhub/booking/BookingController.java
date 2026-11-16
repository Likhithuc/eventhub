package com.eventhub.booking;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.booking.dto.BookingDtos.BookingResponse;
import com.eventhub.booking.dto.BookingDtos.BookingSummary;
import com.eventhub.booking.dto.BookingDtos.CreateBookingRequest;
import com.eventhub.common.response.PageResponse;
import com.eventhub.security.CurrentUser;

import jakarta.validation.Valid;

/** Attendee bookings. Any signed-in user can book; users only ever see their own bookings. */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    /**
     * Book tickets. Send a unique {@code Idempotency-Key} per booking attempt and reuse it
     * when retrying: 201 = created, 200 = the earlier booking for that key.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> create(CurrentUser user,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingService.CreateResult result = service.create(user.id(), request, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.booking());
    }

    /** scope = UPCOMING (default), PAST or CANCELLED. */
    @GetMapping("/me")
    public PageResponse<BookingSummary> mine(CurrentUser user,
            @RequestParam(defaultValue = "UPCOMING") BookingService.Scope scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listMine(user.id(), scope, page, size);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(CurrentUser user, @PathVariable UUID bookingId) {
        return service.get(user, bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(CurrentUser user, @PathVariable UUID bookingId) {
        return service.cancel(user, bookingId);
    }
}
