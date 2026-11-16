package com.eventhub.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventhub.common.response.PageResponse;
import com.eventhub.event.dto.EventDetailResponse;
import com.eventhub.event.dto.EventSummaryResponse;

import jakarta.validation.constraints.Size;

/** Public event discovery. No authentication required; only PUBLISHED events are returned. */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventDiscoveryService discovery;

    public EventController(EventDiscoveryService discovery) {
        this.discovery = discovery;
    }

    /**
     * Upcoming published events, soonest first.
     * Example: GET /api/v1/events?city=Bengaluru&category=music&from=2026-12-01&page=0&size=12
     */
    @GetMapping
    public PageResponse<EventSummaryResponse> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 80) String city,
            @RequestParam(required = false) @Size(max = 60) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return discovery.search(new EventDiscoveryService.SearchCriteria(q, city, category, from, to), page, size);
    }

    /** Cities that currently have upcoming published events (for the city filter). */
    @GetMapping("/cities")
    public List<String> cities() {
        return discovery.upcomingCities();
    }

    @GetMapping("/{eventId}")
    public EventDetailResponse get(@PathVariable UUID eventId) {
        return discovery.getPublishedEvent(eventId);
    }
}
