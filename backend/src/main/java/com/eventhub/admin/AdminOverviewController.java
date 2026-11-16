package com.eventhub.admin;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.admin.audit.AuditLogService;
import com.eventhub.booking.BookingRepository;
import com.eventhub.common.response.PageResponse;
import com.eventhub.event.EventRepository;
import com.eventhub.event.EventStatus;
import com.eventhub.organizer.OrganizerProfile.VerificationStatus;
import com.eventhub.organizer.OrganizerProfileRepository;
import com.eventhub.user.Role;
import com.eventhub.user.UserRepository;
import com.eventhub.user.UserStatus;

/** Platform metrics and the admin activity log. Booking metrics arrive with bookings (Phase 6). */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOverviewController {

    private final UserRepository users;
    private final OrganizerProfileRepository profiles;
    private final EventRepository events;
    private final AuditLogService audit;
    private final BookingRepository bookings;
    private final Clock clock;

    public AdminOverviewController(UserRepository users, OrganizerProfileRepository profiles, EventRepository events,
            AuditLogService audit, BookingRepository bookings, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.events = events;
        this.audit = audit;
        this.bookings = bookings;
        this.clock = clock;
    }

    public record Overview(UserStats users, OrganizerStats organizers, Map<EventStatus, Long> eventsByStatus,
            long pendingApproval, long upcomingPublished, BookingStats bookings) {
    }

    public record BookingStats(long confirmed, long ticketsSold) {
    }

    public record UserStats(long total, long suspended, long organizers, long admins) {
    }

    public record OrganizerStats(long total, long verified, long pendingVerification) {
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Overview overview() {
        Map<EventStatus, Long> byStatus = new EnumMap<>(EventStatus.class);
        for (EventStatus s : EventStatus.values()) byStatus.put(s, 0L);
        events.countAllByStatus().forEach(c -> byStatus.put(c.getStatus(), c.getCount()));
        BookingRepository.EventBookingStats b = bookings.statsAll();
        return new Overview(
                new UserStats(users.count(), users.countByStatus(UserStatus.SUSPENDED),
                        users.countWithRole(Role.ORGANIZER), users.countWithRole(Role.ADMIN)),
                new OrganizerStats(profiles.count(), profiles.countByVerificationStatus(VerificationStatus.VERIFIED),
                        profiles.countByVerificationStatus(VerificationStatus.PENDING)),
                byStatus,
                byStatus.get(EventStatus.PENDING_APPROVAL),
                events.countUpcomingPublishedAll(clock.instant()),
                new BookingStats(b.getBookings(), b.getTickets() == null ? 0 : b.getTickets()));
    }

    @GetMapping("/audit-log")
    public PageResponse<AuditLogService.EntryResponse> auditLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return audit.recent(page, size);
    }
}
