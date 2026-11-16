package com.eventhub.organizer.dto;

import java.time.Instant;
import java.util.UUID;

import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.organizer.OrganizerProfile.VerificationStatus;

public record OrganizerProfileResponse(
        UUID id,
        String displayName,
        String description,
        String contactEmail,
        VerificationStatus verificationStatus,
        Instant createdAt) {

    public static OrganizerProfileResponse from(OrganizerProfile p) {
        return new OrganizerProfileResponse(p.getId(), p.getDisplayName(), p.getDescription(), p.getContactEmail(),
                p.getVerificationStatus(), p.getCreatedAt());
    }
}
