package com.eventhub.organizer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizerProfileRequest(
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @Size(max = 2000) String description,
        @Email @Size(max = 255) String contactEmail) {
}
