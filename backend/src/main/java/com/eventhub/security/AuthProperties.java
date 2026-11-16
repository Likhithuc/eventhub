package com.eventhub.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwtSecret             Base64-encoded HMAC key, at least 256 bits.
 * @param issuer                "iss" claim written to and required on access tokens.
 * @param accessTokenTtl        Lifetime of JWT access tokens (keep short).
 * @param refreshTokenTtl       Lifetime of refresh tokens.
 * @param refreshCookieSecure   Mark the refresh cookie Secure (true everywhere except plain-HTTP localhost).
 * @param refreshCookieSameSite SameSite attribute of the refresh cookie (Strict, Lax or None).
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean refreshCookieSecure,
        String refreshCookieSameSite) {
}
