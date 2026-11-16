package com.eventhub.admin.event;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.admin.event.AdminEventDtos.AdminEventDetail;
import com.eventhub.admin.event.AdminEventDtos.AdminEventSummary;
import com.eventhub.admin.event.AdminEventDtos.ReasonRequest;
import com.eventhub.common.response.PageResponse;
import com.eventhub.event.EventStatus;
import com.eventhub.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/events")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEventController {

    private final AdminEventService service;

    public AdminEventController(AdminEventService service) {
        this.service = service;
    }

    /** e.g. GET /api/v1/admin/events?status=PENDING_APPROVAL */
    @GetMapping
    public PageResponse<AdminEventSummary> list(
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, q, page, size);
    }

    @GetMapping("/{eventId}")
    public AdminEventDetail get(@PathVariable UUID eventId) {
        return service.get(eventId);
    }

    @PostMapping("/{eventId}/approve")
    public AdminEventDetail approve(CurrentUser admin, @PathVariable UUID eventId) {
        return service.approve(admin.id(), eventId);
    }

    @PostMapping("/{eventId}/reject")
    public AdminEventDetail reject(CurrentUser admin, @PathVariable UUID eventId,
            @Valid @RequestBody ReasonRequest request) {
        return service.reject(admin.id(), eventId, request.reason());
    }

    @PostMapping("/{eventId}/unpublish")
    public AdminEventDetail unpublish(CurrentUser admin, @PathVariable UUID eventId,
            @Valid @RequestBody ReasonRequest request) {
        return service.unpublish(admin.id(), eventId, request.reason());
    }
}
