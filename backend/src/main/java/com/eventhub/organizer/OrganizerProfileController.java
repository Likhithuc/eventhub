package com.eventhub.organizer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.common.exception.ApiException;
import com.eventhub.organizer.dto.OrganizerProfileRequest;
import com.eventhub.organizer.dto.OrganizerProfileResponse;
import com.eventhub.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * The signed-in user's own organizer profile. Open to any authenticated user,
 * because creating a profile is how an attendee becomes an organizer.
 * After creating one, the client should refresh its access token to pick up
 * the new ORGANIZER role.
 */
@RestController
@RequestMapping("/api/v1/me/organizer-profile")
public class OrganizerProfileController {

    private final OrganizerProfileService service;

    public OrganizerProfileController(OrganizerProfileService service) {
        this.service = service;
    }

    @GetMapping
    public OrganizerProfileResponse get(CurrentUser user) {
        return service.findForUser(user.id()).map(OrganizerProfileResponse::from)
                .orElseThrow(() -> ApiException.notFound("You don't have an organizer profile yet"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizerProfileResponse create(CurrentUser user, @Valid @RequestBody OrganizerProfileRequest request) {
        return OrganizerProfileResponse.from(service.create(user.id(), request));
    }

    @PutMapping
    public OrganizerProfileResponse update(CurrentUser user, @Valid @RequestBody OrganizerProfileRequest request) {
        return OrganizerProfileResponse.from(service.update(user.id(), request));
    }
}
