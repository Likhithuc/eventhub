package com.eventhub.organizer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.common.exception.ApiException;
import com.eventhub.organizer.dto.OrganizerProfileRequest;
import com.eventhub.user.EmailNormalizer;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.eventhub.user.UserService;

/**
 * "Become an organizer": any active user can create one organizer profile,
 * which also grants the ORGANIZER role. New profiles start PENDING
 * verification; admins verify them (Phase 5).
 */
@Service
public class OrganizerProfileService {

    private final OrganizerProfileRepository profiles;
    private final UserService users;

    public OrganizerProfileService(OrganizerProfileRepository profiles, UserService users) {
        this.profiles = profiles;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<OrganizerProfile> findForUser(UUID userId) {
        return profiles.findByUserId(userId);
    }

    /** The caller's profile, or 409 telling them to create one first. */
    @Transactional(readOnly = true)
    public OrganizerProfile requireForUser(UUID userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> ApiException.conflict("Create your organizer profile first"));
    }

    @Transactional
    public OrganizerProfile create(UUID userId, OrganizerProfileRequest req) {
        User user = users.getActiveUser(userId);
        if (profiles.findByUserId(userId).isPresent()) {
            throw ApiException.conflict("You already have an organizer profile");
        }
        OrganizerProfile profile = new OrganizerProfile(user, req.displayName().trim(), blankToNull(req.description()),
                contactEmail(req, user));
        try {
            profile = profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("You already have an organizer profile");
        }
        user.addRole(Role.ORGANIZER);
        return profile;
    }

    @Transactional
    public OrganizerProfile update(UUID userId, OrganizerProfileRequest req) {
        User user = users.getActiveUser(userId);
        OrganizerProfile profile = profiles.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("You don't have an organizer profile yet"));
        profile.update(req.displayName().trim(), blankToNull(req.description()), contactEmail(req, user));
        return profile;
    }

    private static String contactEmail(OrganizerProfileRequest req, User user) {
        String email = EmailNormalizer.normalize(req.contactEmail());
        return email == null || email.isBlank() ? user.getEmail() : email;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
