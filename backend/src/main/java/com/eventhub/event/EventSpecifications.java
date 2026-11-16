package com.eventhub.event;

import java.time.Instant;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.eventhub.category.Category;

import jakarta.persistence.criteria.Join;

/** Composable filters for public event search. Each returns null when the filter is unused. */
public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> hasStatus(EventStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Event> endsAfter(Instant instant) {
        return (root, query, cb) -> cb.greaterThan(root.get("endsAt"), instant);
    }

    public static Specification<Event> startsAtOrAfter(Instant instant) {
        return instant == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), instant);
    }

    public static Specification<Event> startsBefore(Instant instant) {
        return instant == null ? null : (root, query, cb) -> cb.lessThan(root.get("startsAt"), instant);
    }

    public static Specification<Event> titleContains(String term) {
        if (term == null || term.isBlank()) return null;
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\');
    }

    public static Specification<Event> inCity(String city) {
        if (city == null || city.isBlank()) return null;
        String value = city.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("city")), value);
    }

    public static Specification<Event> inCategory(String slug) {
        if (slug == null || slug.isBlank()) return null;
        String value = slug.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> {
            Join<Event, Category> category = root.join("category");
            return cb.equal(category.get("slug"), value);
        };
    }

    /** Treat user input literally in LIKE patterns. */
    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
