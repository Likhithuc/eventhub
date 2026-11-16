package com.eventhub.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.eventhub.security.AuthProperties;

/**
 * Builds the refresh-token cookie: HttpOnly (not readable by JavaScript),
 * scoped to the auth endpoints only, Secure and SameSite configurable per environment.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "eventhub_refresh";
    static final String PATH = "/api/v1/auth";

    private final AuthProperties props;

    public RefreshCookie(AuthProperties props) {
        this.props = props;
    }

    public ResponseCookie create(String rawToken) {
        return base(rawToken).maxAge(props.refreshTokenTtl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(props.refreshCookieSecure())
                .sameSite(props.refreshCookieSameSite())
                .path(PATH);
    }
}
