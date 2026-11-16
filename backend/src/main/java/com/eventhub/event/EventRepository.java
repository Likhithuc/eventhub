package com.eventhub.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /** Search with category and organizer fetched in the same query (no N+1 on the list page). */
    @Override
    @EntityGraph(attributePaths = {"category", "organizer"})
    Page<Event> findAll(Specification<Event> spec, Pageable pageable);

    @Query("""
            select e from Event e
            join fetch e.category
            join fetch e.organizer
            where e.id = :id and e.status = :status
            """)
    Optional<Event> findByIdAndStatus(@Param("id") UUID id, @Param("status") EventStatus status);

    @Query("""
            select distinct e.city from Event e
            where e.status = com.eventhub.event.EventStatus.PUBLISHED and e.endsAt > :now
            order by e.city
            """)
    List<String> findPublishedUpcomingCities(@Param("now") Instant now);

    /** Row lock for moderation decisions, so two admins cannot act on the same event at once. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    @Query("select e.status as status, count(e) as count from Event e group by e.status")
    List<StatusCount> countAllByStatus();

    interface OrganizerEventCount {
        UUID getOrganizerId();
        long getEvents();
        long getPublished();
    }

    @Query("""
            select e.organizer.id as organizerId, count(e) as events,
                   sum(case when e.status = com.eventhub.event.EventStatus.PUBLISHED then 1 else 0 end) as published
            from Event e where e.organizer.id in :organizerIds group by e.organizer.id
            """)
    List<OrganizerEventCount> countByOrganizers(@Param("organizerIds") Collection<UUID> organizerIds);

    @Query("select count(e) from Event e where e.status = com.eventhub.event.EventStatus.PUBLISHED and e.endsAt > :now")
    long countUpcomingPublishedAll(@Param("now") Instant now);

    /** An organizer's own events (any status), newest start first. */
    @EntityGraph(attributePaths = {"category"})
    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    Page<Event> findByOrganizerIdAndStatus(UUID organizerId, EventStatus status, Pageable pageable);

    interface StatusCount {
        EventStatus getStatus();
        long getCount();
    }

    @Query("select e.status as status, count(e) as count from Event e where e.organizer.id = :organizerId group by e.status")
    List<StatusCount> countByStatus(@Param("organizerId") UUID organizerId);

    @Query("""
            select count(e) from Event e
            where e.organizer.id = :organizerId and e.status = com.eventhub.event.EventStatus.PUBLISHED and e.endsAt > :now
            """)
    long countUpcomingPublished(@Param("organizerId") UUID organizerId, @Param("now") Instant now);

    /** Ticket type count and total capacity per event (all statuses), for organizer lists. */
    interface TicketStats {
        UUID getEventId();
        long getTicketTypes();
        long getCapacity();
    }

    @Query("""
            select t.event.id as eventId, count(t) as ticketTypes, coalesce(sum(t.totalQuantity), 0) as capacity
            from TicketType t where t.event.id in :eventIds group by t.event.id
            """)
    List<TicketStats> findTicketStats(@Param("eventIds") Collection<UUID> eventIds);

    /** Price range of active ticket types per event, for list cards. */
    interface PriceRange {
        UUID getEventId();
        BigDecimal getMinPrice();
        BigDecimal getMaxPrice();
        String getCurrency();
    }

    @Query("""
            select t.event.id as eventId, min(t.price) as minPrice, max(t.price) as maxPrice, min(t.currency) as currency
            from TicketType t
            where t.event.id in :eventIds and t.status = com.eventhub.ticket.TicketType.Status.ACTIVE
            group by t.event.id
            """)
    List<PriceRange> findPriceRanges(@Param("eventIds") Collection<UUID> eventIds);
}
