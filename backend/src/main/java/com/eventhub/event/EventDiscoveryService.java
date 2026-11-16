package com.eventhub.event;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.common.exception.ApiException;
import com.eventhub.common.response.PageResponse;
import com.eventhub.event.dto.CategoryRef;
import com.eventhub.event.dto.EventDetailResponse;
import com.eventhub.event.dto.EventDetailResponse.OrganizerRef;
import com.eventhub.event.dto.EventDetailResponse.TicketTypeResponse;
import com.eventhub.event.dto.EventSummaryResponse;
import com.eventhub.event.dto.PriceInfo;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.ticket.TicketType;

/**
 * Public, read-only event discovery. Every query here is restricted to
 * PUBLISHED events, so drafts, pending, rejected and cancelled events can
 * never leak through the public API.
 */
@Service
public class EventDiscoveryService {

    public static final int MAX_PAGE_SIZE = 50;
    private static final String DEFAULT_CURRENCY = "INR";

    private final EventRepository events;
    private final ZoneId zone;
    private final Clock clock;

    public EventDiscoveryService(EventRepository events,
            @Value("${app.timezone:Asia/Kolkata}") String timezone, Clock clock) {
        this.events = events;
        this.zone = ZoneId.of(timezone);
        this.clock = clock;
    }

    /**
     * @param from inclusive local date (platform timezone) the event must start on or after
     * @param to   inclusive local date the event must start on or before
     */
    public record SearchCriteria(String q, String city, String category, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> search(SearchCriteria criteria, int page, int size) {
        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw ApiException.badRequest("'from' must be on or before 'to'");
        }
        Instant now = clock.instant();
        Specification<Event> spec = Stream.of(
                        EventSpecifications.hasStatus(EventStatus.PUBLISHED),
                        EventSpecifications.endsAfter(now),
                        EventSpecifications.titleContains(criteria.q()),
                        EventSpecifications.inCity(criteria.city()),
                        EventSpecifications.inCategory(criteria.category()),
                        EventSpecifications.startsAtOrAfter(startOfDay(criteria.from())),
                        EventSpecifications.startsBefore(startOfDay(criteria.to() == null ? null : criteria.to().plusDays(1))))
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElseThrow();

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id")));
        Page<Event> result = events.findAll(spec, pageable);

        Map<UUID, EventRepository.PriceRange> prices = result.isEmpty() ? Map.of()
                : events.findPriceRanges(result.map(Event::getId).toList()).stream()
                        .collect(Collectors.toMap(EventRepository.PriceRange::getEventId, Function.identity()));

        return PageResponse.from(result.map(e -> toSummary(e, prices.get(e.getId()))));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getPublishedEvent(UUID id) {
        Event event = events.findByIdAndStatus(id, EventStatus.PUBLISHED)
                // Same 404 for missing and non-public events, so drafts cannot be probed.
                .orElseThrow(() -> ApiException.notFound("Event not found"));
        Instant now = clock.instant();

        List<TicketType> active = event.getTicketTypes().stream()
                .filter(t -> t.getStatus() == TicketType.Status.ACTIVE)
                .sorted(Comparator.comparing(TicketType::getPrice).thenComparing(TicketType::getName))
                .toList();
        List<TicketTypeResponse> tickets = active.stream()
                .map(t -> new TicketTypeResponse(t.getId(), t.getName(), t.getPrice(), t.getCurrency(),
                        t.getTotalQuantity(), t.getAvailable(), t.getSalesStartAt(), t.getSalesEndAt(),
                        t.isOnSale(now) && event.getStartsAt().isAfter(now) && t.getAvailable() > 0,
                        t.getAvailable() == 0))
                .toList();

        BigDecimal min = active.stream().map(TicketType::getPrice).min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = active.stream().map(TicketType::getPrice).max(BigDecimal::compareTo).orElse(null);
        String currency = active.isEmpty() ? DEFAULT_CURRENCY : active.getFirst().getCurrency();

        OrganizerProfile org = event.getOrganizer();
        return new EventDetailResponse(
                event.getId(), event.getTitle(), event.getDescription(), CategoryRef.from(event.getCategory()),
                event.getVenueName(), event.getAddress(), event.getCity(),
                event.getStartsAt(), event.getEndsAt(), !event.getEndsAt().isAfter(now),
                new OrganizerRef(org.getId(), org.getDisplayName(), org.getDescription(),
                        org.getVerificationStatus() == OrganizerProfile.VerificationStatus.VERIFIED),
                tickets, PriceInfo.of(min, max, currency), event.getCancellationCutoffHours());
    }

    @Transactional(readOnly = true)
    public List<String> upcomingCities() {
        return events.findPublishedUpcomingCities(clock.instant());
    }

    private EventSummaryResponse toSummary(Event e, EventRepository.PriceRange price) {
        PriceInfo priceInfo = price == null
                ? PriceInfo.of(null, null, DEFAULT_CURRENCY)
                : PriceInfo.of(price.getMinPrice(), price.getMaxPrice(), price.getCurrency());
        return new EventSummaryResponse(e.getId(), e.getTitle(), CategoryRef.from(e.getCategory()),
                e.getVenueName(), e.getCity(), e.getStartsAt(), e.getEndsAt(),
                e.getOrganizer().getDisplayName(), priceInfo);
    }

    private Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(zone).toInstant();
    }
}
