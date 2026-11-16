package com.eventhub.ticket;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    /**
     * Locks the given ticket types (SELECT ... FOR UPDATE) so concurrent bookings
     * for the same tickets run one after another. Always locked in id order, so
     * two bookings touching the same rows can never deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id in :ids and t.event.id = :eventId order by t.id")
    List<TicketType> lockForBooking(@Param("ids") Collection<UUID> ids, @Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.event.id = :eventId order by t.id")
    List<TicketType> lockAllForEvent(@Param("eventId") UUID eventId);
}
