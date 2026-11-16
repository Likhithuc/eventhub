package com.eventhub.admin.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    Page<AuditLogEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLogEntry> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(AuditLogEntry.TargetType type, UUID targetId);
}
