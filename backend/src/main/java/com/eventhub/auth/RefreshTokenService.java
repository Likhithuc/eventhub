package com.eventhub.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.common.exception.ApiException;
import com.eventhub.security.AuthProperties;
import com.eventhub.user.User;

/**
 * Opaque, rotating refresh tokens. The raw token only ever exists in the
 * client's HttpOnly cookie; the database stores its SHA-256 hash.
 *
 * <p>Reuse detection: presenting an already-rotated token revokes every
 * session of that user (a sign the token was stolen). A short grace period
 * tolerates benign races such as two browser tabs refreshing at once.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    static final Duration REUSE_GRACE = Duration.ofSeconds(30);

    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final AuthProperties props;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties props) {
        this.repository = repository;
        this.props = props;
    }

    public record Rotation(User user, String newRawToken) {
    }

    @Transactional
    public String create(User user) {
        Instant now = Instant.now();
        repository.deleteExpiredForUser(user.getId(), now);
        return createToken(user, now).raw();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        User user = current.getUser();

        if (current.isRevoked()) {
            boolean benignRace = current.getReplacedBy() != null
                    && current.getRevokedAt().isAfter(now.minus(REUSE_GRACE));
            if (!benignRace) {
                int revoked = repository.revokeAllForUser(user.getId(), now);
                log.warn("Refresh token reuse detected for user {}; revoked {} active session(s)", user.getId(), revoked);
            }
            throw ApiException.unauthorized("Refresh token is no longer valid");
        }
        if (current.isExpired(now)) {
            throw ApiException.unauthorized("Refresh token expired");
        }
        if (!user.isActive()) {
            repository.revokeAllForUser(user.getId(), now);
            throw ApiException.forbidden("Account is suspended");
        }

        Created next = createToken(user, now);
        current.revoke(now, next.entity().getId());
        return new Rotation(user, next.raw());
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(Instant.now(), null));
    }

    private record Created(RefreshToken entity, String raw) {
    }

    private Created createToken(User user, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken saved = repository.saveAndFlush(
                new RefreshToken(user, hash(raw), now.plus(props.refreshTokenTtl())));
        return new Created(saved, raw);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
