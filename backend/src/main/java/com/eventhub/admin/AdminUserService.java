package com.eventhub.admin;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.admin.audit.AuditLogEntry.Action;
import com.eventhub.admin.audit.AuditLogEntry.TargetType;
import com.eventhub.admin.audit.AuditLogService;
import com.eventhub.auth.RefreshTokenRepository;
import com.eventhub.common.exception.ApiException;
import com.eventhub.common.response.PageResponse;
import com.eventhub.event.EventSpecifications;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.eventhub.user.UserRepository;
import com.eventhub.user.UserStatus;
import com.eventhub.user.dto.UserResponse;

import jakarta.persistence.criteria.Join;

@Service
public class AdminUserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditLogService audit;
    private final Clock clock;

    public AdminUserService(UserRepository users, RefreshTokenRepository refreshTokens, AuditLogService audit,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, Role role, UserStatus status, int page, int size) {
        Specification<User> spec = Stream.<Specification<User>>of(
                        nameOrEmailContains(q),
                        role == null ? null : hasRole(role),
                        status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status))
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.from(users.findAll(spec, pageable).map(UserResponse::from));
    }

    /**
     * Suspends or reactivates an account. Suspension also revokes every
     * refresh token, so the user is signed out everywhere once their current
     * short-lived access token expires.
     */
    @Transactional
    public UserResponse setStatus(UUID adminId, UUID userId, UserStatus status) {
        if (adminId.equals(userId)) {
            throw ApiException.badRequest("You can't change the status of your own account");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getStatus() == status) {
            return UserResponse.from(user);
        }
        user.setStatus(status);
        if (status == UserStatus.SUSPENDED) {
            refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        }
        audit.record(adminId, status == UserStatus.SUSPENDED ? Action.USER_SUSPENDED : Action.USER_REACTIVATED,
                TargetType.USER, user.getId(), user.getName() + " <" + user.getEmail() + ">", null);
        return UserResponse.from(user);
    }

    private static Specification<User> nameOrEmailContains(String q) {
        if (q == null || q.isBlank()) return null;
        String pattern = "%" + EventSpecifications.escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(root.get("email"), pattern, '\\'));
    }

    private static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> {
            Join<User, Role> roles = root.join("roles");
            if (query != null) query.distinct(true);
            return cb.equal(roles, role);
        };
    }
}
