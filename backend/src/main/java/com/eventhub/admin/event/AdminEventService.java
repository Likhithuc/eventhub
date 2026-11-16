package com.eventhub.admin.event;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.admin.audit.AuditLogEntry.Action;
import com.eventhub.admin.audit.AuditLogEntry.TargetType;
import com.eventhub.admin.audit.AuditLogService;
import com.eventhub.admin.event.AdminEventDtos.AdminEventDetail;
import com.eventhub.admin.event.AdminEventDtos.AdminEventSummary;
import com.eventhub.admin.event.AdminEventDtos.OrganizerInfo;
import com.eventhub.common.exception.ApiException;
import com.eventhub.common.response.PageResponse;
import com.eventhub.event.Event;
import com.eventhub.event.EventRepository;
import com.eventhub.event.EventSpecifications;
import com.eventhub.event.EventStatus;
import com.eventhub.event.dto.CategoryRef;
import com.eventhub.event.organizer.dto.OrganizerEventResponse;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.user.User;
import com.eventhub.user.UserRepository;

/**
 * Admin moderation of events. Decisions lock the event row so two admins
 * cannot approve and reject the same event concurrently, and every decision
 * is written to the audit log in the same transaction.
 */
@Service
public class AdminEventService {

    private final EventRepository events;
    private final UserRepository users;
    private final AuditLogService audit;
    private final Clock clock;

    public AdminEventService(EventRepository events, UserRepository users, AuditLogService audit, Clock clock) {
        this.events = events;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /** Moderation list. Pending events come oldest-submitted first, so nothing waits forever. */
    @Transactional(readOnly = true)
    public PageResponse<AdminEventSummary> list(EventStatus status, String q, int page, int size) {
        Specification<Event> spec = Stream.of(
                        status == null ? null : EventSpecifications.hasStatus(status),
                        EventSpecifications.titleContains(q))
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
        Sort sort = status == EventStatus.PENDING_APPROVAL
                ? Sort.by(Sort.Order.asc("submittedAt").nullsLast(), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("startsAt"), Sort.Order.asc("id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50), sort);

        return PageResponse.from(events.findAll(spec, pageable).map(e -> {
            OrganizerProfile org = e.getOrganizer();
            return new AdminEventSummary(e.getId(), e.getTitle(), CategoryRef.from(e.getCategory()), e.getCity(),
                    e.getStartsAt(), e.getStatus(), org.getId(), org.getDisplayName(), org.getVerificationStatus(),
                    e.getSubmittedAt(), e.getReviewedAt());
        }));
    }

    @Transactional(readOnly = true)
    public AdminEventDetail get(UUID eventId) {
        return detail(load(eventId, false));
    }

    @Transactional
    public AdminEventDetail approve(UUID adminId, UUID eventId) {
        Event event = load(eventId, true);
        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Only events pending approval can be approved (this one is " + label(event) + ")");
        }
        Instant now = clock.instant();
        if (!event.getStartsAt().isAfter(now)) {
            throw ApiException.conflict("This event's start time has passed. Reject it so the organizer can update the dates.");
        }
        event.approve(adminId, now);
        audit.record(adminId, Action.EVENT_APPROVED, TargetType.EVENT, event.getId(), event.getTitle(), null);
        return detail(event);
    }

    @Transactional
    public AdminEventDetail reject(UUID adminId, UUID eventId, String reason) {
        Event event = load(eventId, true);
        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Only events pending approval can be rejected (this one is " + label(event) + ")");
        }
        event.reject(adminId, clock.instant(), reason.trim());
        audit.record(adminId, Action.EVENT_REJECTED, TargetType.EVENT, event.getId(), event.getTitle(), reason.trim());
        return detail(event);
    }

    @Transactional
    public AdminEventDetail unpublish(UUID adminId, UUID eventId, String reason) {
        Event event = load(eventId, true);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw ApiException.conflict("Only published events can be unpublished (this one is " + label(event) + ")");
        }
        if (!event.getEndsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("This event has already ended");
        }
        event.unpublish(adminId, clock.instant(), reason.trim());
        audit.record(adminId, Action.EVENT_UNPUBLISHED, TargetType.EVENT, event.getId(), event.getTitle(), reason.trim());
        return detail(event);
    }

    private Event load(UUID eventId, boolean forUpdate) {
        return (forUpdate ? events.findByIdForUpdate(eventId) : events.findById(eventId))
                .orElseThrow(() -> ApiException.notFound("Event not found"));
    }

    private AdminEventDetail detail(Event e) {
        OrganizerProfile org = e.getOrganizer();
        User owner = org.getUser();
        String reviewer = e.getReviewedBy() == null ? null
                : users.findById(e.getReviewedBy()).map(User::getName).orElse("Deleted user");
        return new AdminEventDetail(
                OrganizerEventResponse.from(e),
                new OrganizerInfo(org.getId(), org.getDisplayName(), org.getDescription(), org.getContactEmail(),
                        org.getVerificationStatus(), owner.getId(), owner.getName(), owner.getEmail()),
                e.getRejectionReason(), e.getReviewedAt(), reviewer,
                audit.forTarget(TargetType.EVENT, e.getId()));
    }

    private static String label(Event e) {
        return e.getStatus().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
