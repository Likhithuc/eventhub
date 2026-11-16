package com.eventhub.auth.dto;

import com.eventhub.user.dto.UserResponse;

/** Returned by register, login and refresh. The refresh token travels separately in an HttpOnly cookie. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static AuthResponse bearer(String accessToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
