package com.eventhub.security;

import java.util.Set;
import java.util.UUID;

/** The authenticated caller, resolved from the access token. Declare it as a controller parameter. */
public record CurrentUser(UUID id, String email, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
