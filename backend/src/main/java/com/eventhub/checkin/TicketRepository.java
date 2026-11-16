package com.eventhub.checkin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByCode(String code);

    /** Locked so two simultaneous scans of the same ticket are processed one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.code = :code")
    Optional<Ticket> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.token = :token")
    Optional<Ticket> findByTokenForUpdate(@Param("token") String token);

    @Query("select count(t) from Ticket t where t.event.id = :eventId and t.status = com.eventhub.checkin.Ticket.Status.VALID")
    long countValidForEvent(@Param("eventId") UUID eventId);

    @Query("""
            select count(t) from Ticket t
            where t.event.id = :eventId and t.status = com.eventhub.checkin.Ticket.Status.VALID and t.checkedInAt is not null
            """)
    long countCheckedInForEvent(@Param("eventId") UUID eventId);
}
