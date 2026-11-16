package com.eventhub.organizer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, UUID> {

    Optional<OrganizerProfile> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    Page<OrganizerProfile> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<OrganizerProfile> findByVerificationStatus(OrganizerProfile.VerificationStatus status, Pageable pageable);

    long countByVerificationStatus(OrganizerProfile.VerificationStatus status);
}
