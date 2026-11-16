package com.eventhub.admin.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.admin.audit.AuditLogEntry.Action;
import com.eventhub.admin.audit.AuditLogEntry.TargetType;
import com.eventhub.common.response.PageResponse;
import com.eventhub.user.User;
import com.eventhub.user.UserRepository;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository entries;
    private final UserRepository users;

    public AuditLogService(AuditLogRepository entries, UserRepository users) {
        this.entries = entries;
        this.users = users;
    }

    /** Recorded in the caller's transaction, so the log entry commits only if the action does. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, Action action, TargetType targetType, UUID targetId, String summary, String details) {
        entries.save(new AuditLogEntry(actorId, action, targetType, targetId, summary, details));
        log.info("ADMIN_AUDIT actor={} action={} target={}:{}", actorId, action, targetType, targetId);
    }

    public record EntryResponse(UUID id, Instant createdAt, Action action, TargetType targetType, UUID targetId,
            String summary, String details, UUID actorId, String actorName) {
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryResponse> recent(int page, int size) {
        Page<AuditLogEntry> result = entries.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));
        return PageResponse.from(withActors(result));
    }

    @Transactional(readOnly = true)
    public List<EntryResponse> forTarget(TargetType type, UUID targetId) {
        List<AuditLogEntry> list = entries.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(type, targetId);
        Map<UUID, String> names = actorNames(list.stream().map(AuditLogEntry::getActorId).collect(Collectors.toSet()));
        return list.stream().map(e -> toResponse(e, names)).toList();
    }

    private Page<EntryResponse> withActors(Page<AuditLogEntry> page) {
        Map<UUID, String> names = actorNames(page.stream().map(AuditLogEntry::getActorId).collect(Collectors.toSet()));
        return page.map(e -> toResponse(e, names));
    }

    private Map<UUID, String> actorNames(Set<UUID> ids) {
        ids.remove(null);
        return ids.isEmpty() ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getName));
    }

    private static EntryResponse toResponse(AuditLogEntry e, Map<UUID, String> names) {
        return new EntryResponse(e.getId(), e.getCreatedAt(), e.getAction(), e.getTargetType(), e.getTargetId(),
                e.getSummary(), e.getDetails(), e.getActorId(),
                e.getActorId() == null ? null : names.getOrDefault(e.getActorId(), "Deleted user"));
    }
}
