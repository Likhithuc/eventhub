package com.eventhub.checkin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    Optional<CheckIn> findByTicketId(UUID ticketId);

    @Query("""
            select c from CheckIn c join fetch c.ticket t join fetch t.ticketType join fetch t.booking b join fetch b.user
            where c.eventId = :eventId order by c.checkedInAt desc
            """)
    List<CheckIn> findRecentForEvent(@Param("eventId") UUID eventId, Pageable pageable);
}
