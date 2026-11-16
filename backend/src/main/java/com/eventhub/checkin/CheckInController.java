package com.eventhub.checkin;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.checkin.CheckInService.CheckInResponse;
import com.eventhub.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Door check-in for an organizer's own event. Ownership is checked by the service. */
@RestController
@RequestMapping("/api/v1/organizer/events/{eventId}/check-ins")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
public class CheckInController {

    private final CheckInService service;

    public CheckInController(CheckInService service) {
        this.service = service;
    }

    /** {@code code}: QR payload ("EH1.…") or the manual code ("7K3M-9QX2"). */
    public record CheckInRequest(@NotBlank @Size(max = 100) String code) {
    }

    /**
     * 200 CHECKED_IN · 409 ALREADY_CHECKED_IN / CANCELLED / TOO_EARLY / EVENT_ENDED / NOT_PUBLISHED ·
     * 404 NOT_FOUND. The body always has the same shape so a scanner UI can show it directly.
     */
    @PostMapping
    public ResponseEntity<CheckInResponse> checkIn(CurrentUser staff, @PathVariable UUID eventId,
            @Valid @RequestBody CheckInRequest request) {
        CheckInResponse response = service.checkIn(staff, eventId, request.code());
        HttpStatus status = switch (response.result()) {
            case CHECKED_IN -> HttpStatus.OK;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/summary")
    public CheckInService.Summary summary(CurrentUser staff, @PathVariable UUID eventId) {
        return service.summary(staff, eventId);
    }
}
