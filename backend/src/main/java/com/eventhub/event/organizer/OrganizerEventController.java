package com.eventhub.event.organizer;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.common.response.PageResponse;
import com.eventhub.event.EventStatus;
import com.eventhub.event.organizer.dto.EventRequest;
import com.eventhub.event.organizer.dto.OrganizerEventResponse;
import com.eventhub.event.organizer.dto.OrganizerEventSummary;
import com.eventhub.event.organizer.dto.TicketTypeRequest;
import com.eventhub.security.CurrentUser;

import jakarta.validation.Valid;

/** Organizer event management. Ownership is enforced in {@link OrganizerEventService}. */
@RestController
@RequestMapping("/api/v1/organizer/events")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
public class OrganizerEventController {

    private final OrganizerEventService service;

    public OrganizerEventController(OrganizerEventService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<OrganizerEventSummary> list(CurrentUser user,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listOwn(user, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizerEventResponse create(CurrentUser user, @Valid @RequestBody EventRequest request) {
        return service.create(user, request);
    }

    @GetMapping("/{eventId}")
    public OrganizerEventResponse get(CurrentUser user, @PathVariable UUID eventId) {
        return service.get(user, eventId);
    }

    @PutMapping("/{eventId}")
    public OrganizerEventResponse update(CurrentUser user, @PathVariable UUID eventId,
            @Valid @RequestBody EventRequest request) {
        return service.update(user, eventId, request);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(CurrentUser user, @PathVariable UUID eventId) {
        service.deleteDraft(user, eventId);
    }

    @PostMapping("/{eventId}/submit")
    public OrganizerEventResponse submit(CurrentUser user, @PathVariable UUID eventId) {
        return service.submit(user, eventId);
    }

    @PostMapping("/{eventId}/withdraw")
    public OrganizerEventResponse withdraw(CurrentUser user, @PathVariable UUID eventId) {
        return service.withdraw(user, eventId);
    }

    @PostMapping("/{eventId}/cancel")
    public OrganizerEventResponse cancel(CurrentUser user, @PathVariable UUID eventId) {
        return service.cancel(user, eventId);
    }

    @PostMapping("/{eventId}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizerEventResponse addTicketType(CurrentUser user, @PathVariable UUID eventId,
            @Valid @RequestBody TicketTypeRequest request) {
        return service.addTicketType(user, eventId, request);
    }

    @PutMapping("/{eventId}/ticket-types/{ticketTypeId}")
    public OrganizerEventResponse updateTicketType(CurrentUser user, @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId, @Valid @RequestBody TicketTypeRequest request) {
        return service.updateTicketType(user, eventId, ticketTypeId, request);
    }

    @DeleteMapping("/{eventId}/ticket-types/{ticketTypeId}")
    public OrganizerEventResponse deleteTicketType(CurrentUser user, @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId) {
        return service.deleteTicketType(user, eventId, ticketTypeId);
    }
}
