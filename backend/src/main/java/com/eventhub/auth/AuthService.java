package com.eventhub.auth;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.auth.dto.AuthResponse;
import com.eventhub.auth.dto.LoginRequest;
import com.eventhub.auth.dto.RegisterRequest;
import com.eventhub.common.exception.ApiException;
import com.eventhub.security.AccessTokenService;
import com.eventhub.user.EmailNormalizer;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.eventhub.user.UserRepository;
import com.eventhub.user.dto.UserResponse;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    /** Compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    /** Access-token response plus the raw refresh token for the cookie. */
    public record AuthResult(AuthResponse response, String refreshToken) {
    }

    @Transactional
    public AuthResult register(RegisterRequest req) {
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password is too long");
        }
        String email = EmailNormalizer.normalize(req.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User(req.name().trim(), email, passwordEncoder.encode(req.password()),
                EnumSet.of(Role.ATTENDEE));
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent registration of the same email.
            throw ApiException.conflict("An account with this email already exists");
        }
        return issue(user, refreshTokenService.create(user));
    }

    @Transactional
    public AuthResult login(LoginRequest req) {
        String email = EmailNormalizer.normalize(req.email());
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("Account is suspended");
        }
        return issue(user, refreshTokenService.create(user));
    }

    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Missing refresh token");
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        return issue(rotation.user(), rotation.newRawToken());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    private AuthResult issue(User user, String refreshToken) {
        AuthResponse body = AuthResponse.bearer(
                accessTokenService.issue(user),
                accessTokenService.accessTokenTtlSeconds(),
                UserResponse.from(user));
        return new AuthResult(body, refreshToken);
    }
}
