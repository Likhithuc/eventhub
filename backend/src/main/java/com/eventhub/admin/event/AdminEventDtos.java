package com.eventhub.admin.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eventhub.admin.audit.AuditLogService;
import com.eventhub.event.EventStatus;
import com.eventhub.event.dto.CategoryRef;
import com.eventhub.event.organizer.dto.OrganizerEventResponse;
import com.eventhub.organizer.OrganizerProfile.VerificationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response shapes for admin event moderation. */
public final class AdminEventDtos {

    private AdminEventDtos() {
    }

    public record OrganizerInfo(UUID id, String displayName, String description, String contactEmail,
            VerificationStatus verificationStatus, UUID userId, String userName, String userEmail) {
    }

    public record AdminEventSummary(
            UUID id,
            String title,
            CategoryRef category,
            String city,
            Instant startsAt,
            EventStatus status,
            UUID organizerId,
            String organizerName,
            VerificationStatus organizerVerification,
            Instant submittedAt,
            Instant reviewedAt) {
    }

    public record AdminEventDetail(
            OrganizerEventResponse event,
            OrganizerInfo organizer,
            String rejectionReason,
            Instant reviewedAt,
            String reviewedByName,
            List<AuditLogService.EntryResponse> history) {
    }

    /** Reason shown to the organizer. Required for reject and unpublish. */
    public record ReasonRequest(@NotBlank @Size(min = 5, max = 1000) String reason) {
    }
}
