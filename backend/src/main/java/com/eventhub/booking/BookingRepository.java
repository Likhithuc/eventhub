package com.eventhub.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    boolean existsByReference(String reference);

    @EntityGraph(attributePaths = {"event", "event.category", "event.organizer", "items", "items.ticketType"})
    Optional<Booking> findWithDetailsById(UUID id);

    /** Active bookings for events that haven't ended, soonest event first. */
    @Query(value = """
            select b from Booking b join fetch b.event e join fetch e.category
            where b.user.id = :userId and b.status in (com.eventhub.booking.BookingStatus.CONFIRMED, com.eventhub.booking.BookingStatus.PENDING)
              and e.endsAt > :now
            order by e.startsAt asc, b.id
            """, countQuery = """
            select count(b) from Booking b join b.event e
            where b.user.id = :userId and b.status in (com.eventhub.booking.BookingStatus.CONFIRMED, com.eventhub.booking.BookingStatus.PENDING)
              and e.endsAt > :now
            """)
    Page<Booking> findUpcoming(@Param("userId") UUID userId, @Param("now") Instant now, Pageable pageable);

    @Query(value = """
            select b from Booking b join fetch b.event e join fetch e.category
            where b.user.id = :userId and b.status in (com.eventhub.booking.BookingStatus.CONFIRMED, com.eventhub.booking.BookingStatus.PENDING)
              and e.endsAt <= :now
            order by e.startsAt desc, b.id
            """, countQuery = """
            select count(b) from Booking b join b.event e
            where b.user.id = :userId and b.status in (com.eventhub.booking.BookingStatus.CONFIRMED, com.eventhub.booking.BookingStatus.PENDING)
              and e.endsAt <= :now
            """)
    Page<Booking> findPast(@Param("userId") UUID userId, @Param("now") Instant now, Pageable pageable);

    @Query(value = """
            select b from Booking b join fetch b.event e join fetch e.category
            where b.user.id = :userId and b.status not in (com.eventhub.booking.BookingStatus.CONFIRMED, com.eventhub.booking.BookingStatus.PENDING)
            order by b.cancelledAt desc nulls last, b.createdAt desc
            """, countQuery = """
            select count(b) from Booking b
            where b.user.id = :userId and b.status not in (com.eventhub.booking.BookingStatus.CONFIRMED, com.eventhub.booking.BookingStatus.PENDING)
            """)
    Page<Booking> findCancelled(@Param("userId") UUID userId, Pageable pageable);

    /** Attendee list for an organizer. */
    @Query(value = """
            select b from Booking b join fetch b.user
            where b.event.id = :eventId
            order by b.createdAt desc, b.id
            """, countQuery = "select count(b) from Booking b where b.event.id = :eventId")
    Page<Booking> findForEvent(@Param("eventId") UUID eventId, Pageable pageable);

    @Query("""
            select b from Booking b
            where b.event.id = :eventId and b.status in (com.eventhub.booking.BookingStatus.CONFIRMED, com.eventhub.booking.BookingStatus.PENDING)
            """)
    List<Booking> findActiveForEvent(@Param("eventId") UUID eventId);

    interface EventBookingStats {
        long getBookings();
        Long getTickets();
    }

    @Query("""
            select count(b) as bookings, sum(b.ticketCount) as tickets from Booking b
            where b.event.id = :eventId and b.status = com.eventhub.booking.BookingStatus.CONFIRMED
            """)
    EventBookingStats statsForEvent(@Param("eventId") UUID eventId);

    @Query("""
            select count(b) as bookings, sum(b.ticketCount) as tickets from Booking b
            where b.status = com.eventhub.booking.BookingStatus.CONFIRMED
            """)
    EventBookingStats statsAll();
}
