package com.eventhub.admin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.admin.audit.AuditLogEntry.Action;
import com.eventhub.admin.audit.AuditLogEntry.TargetType;
import com.eventhub.admin.audit.AuditLogService;
import com.eventhub.common.exception.ApiException;
import com.eventhub.common.response.PageResponse;
import com.eventhub.event.EventRepository;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.organizer.OrganizerProfile.VerificationStatus;
import com.eventhub.organizer.OrganizerProfileRepository;
import com.eventhub.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Organizer profile verification. A verified badge is shown on the organizer's public events. */
@RestController
@RequestMapping("/api/v1/admin/organizers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrganizerController {

    private final OrganizerProfileRepository profiles;
    private final EventRepository events;
    private final AuditLogService audit;

    public AdminOrganizerController(OrganizerProfileRepository profiles, EventRepository events, AuditLogService audit) {
        this.profiles = profiles;
        this.events = events;
        this.audit = audit;
    }

    public record OrganizerRow(UUID id, String displayName, String description, String contactEmail,
            VerificationStatus verificationStatus, Instant createdAt, UUID userId, String userName, String userEmail,
            long events, long publishedEvents) {
    }

    public record VerificationRequest(@NotNull VerificationStatus status) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<OrganizerRow> list(
            @RequestParam(required = false) VerificationStatus verification,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<OrganizerProfile> result = verification == null
                ? profiles.findAllBy(pageable)
                : profiles.findByVerificationStatus(verification, pageable);
        Map<UUID, EventRepository.OrganizerEventCount> counts = result.isEmpty() ? Map.of()
                : events.countByOrganizers(result.map(OrganizerProfile::getId).toList()).stream()
                        .collect(Collectors.toMap(EventRepository.OrganizerEventCount::getOrganizerId, Function.identity()));
        return PageResponse.from(result.map(p -> toRow(p, counts.get(p.getId()))));
    }

    @PatchMapping("/{organizerId}/verification")
    @Transactional
    public OrganizerRow setVerification(CurrentUser admin, @PathVariable UUID organizerId,
            @Valid @RequestBody VerificationRequest request) {
        OrganizerProfile profile = profiles.findById(organizerId)
                .orElseThrow(() -> ApiException.notFound("Organizer not found"));
        if (profile.getVerificationStatus() != request.status()) {
            profile.setVerificationStatus(request.status());
            Action action = switch (request.status()) {
                case VERIFIED -> Action.ORGANIZER_VERIFIED;
                case REJECTED -> Action.ORGANIZER_VERIFICATION_REJECTED;
                case PENDING -> Action.ORGANIZER_VERIFICATION_RESET;
            };
            audit.record(admin.id(), action, TargetType.ORGANIZER, profile.getId(), profile.getDisplayName(), null);
        }
        var count = events.countByOrganizers(java.util.List.of(profile.getId())).stream().findFirst().orElse(null);
        return toRow(profile, count);
    }

    private static OrganizerRow toRow(OrganizerProfile p, EventRepository.OrganizerEventCount c) {
        return new OrganizerRow(p.getId(), p.getDisplayName(), p.getDescription(), p.getContactEmail(),
                p.getVerificationStatus(), p.getCreatedAt(), p.getUser().getId(), p.getUser().getName(),
                p.getUser().getEmail(), c == null ? 0 : c.getEvents(), c == null ? 0 : c.getPublished());
    }
}
