package com.eventhub.organizer;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.event.organizer.OrganizerEventService;
import com.eventhub.organizer.dto.OrganizerProfileResponse;
import com.eventhub.security.CurrentUser;

/** Organizer home: profile plus event counts. {@code profile} is null if none has been created yet. */
@RestController
@RequestMapping("/api/v1/organizer")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
public class OrganizerDashboardController {

    private final OrganizerProfileService profiles;
    private final OrganizerEventService events;

    public OrganizerDashboardController(OrganizerProfileService profiles, OrganizerEventService events) {
        this.profiles = profiles;
        this.events = events;
    }

    public record DashboardResponse(OrganizerProfileResponse profile, OrganizerEventService.Stats stats) {
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(CurrentUser user) {
        return profiles.findForUser(user.id())
                .map(p -> new DashboardResponse(OrganizerProfileResponse.from(p), events.stats(p.getId())))
                .orElseGet(() -> new DashboardResponse(null, OrganizerEventService.Stats.EMPTY));
    }
}
