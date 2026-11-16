package com.eventhub.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.category.CategoryRepository;
import com.eventhub.event.Event;
import com.eventhub.event.EventRepository;
import com.eventhub.event.EventStatus;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.organizer.OrganizerProfileRepository;
import com.eventhub.ticket.TicketType;
import com.eventhub.user.Role;
import com.eventhub.user.User;

/** Builds organizers and events directly in the database for discovery tests. */
@Component
public class TestEvents {

    private final TestUsers users;
    private final OrganizerProfileRepository profiles;
    private final CategoryRepository categories;
    private final EventRepository events;

    public TestEvents(TestUsers users, OrganizerProfileRepository profiles, CategoryRepository categories,
            EventRepository events) {
        this.users = users;
        this.profiles = profiles;
        this.categories = categories;
        this.events = events;
    }

    /** A city name no other test uses, so each test sees only its own events. */
    public static String uniqueCity() {
        return "City-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static Instant daysFromNow(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    @Transactional
    public OrganizerProfile organizer(String displayName) {
        User user = users.create("org", EnumSet.of(Role.ORGANIZER, Role.ATTENDEE));
        return profiles.save(new OrganizerProfile(user, displayName, "About " + displayName, user.getEmail()));
    }

    public EventBuilder event(OrganizerProfile organizer, String title) {
        return new EventBuilder(organizer, title);
    }

    public final class EventBuilder {
        private final OrganizerProfile organizer;
        private final String title;
        private String category = "music";
        private String city = "Bengaluru";
        private Instant startsAt = daysFromNow(7);
        private Instant endsAt;
        private EventStatus status = EventStatus.PUBLISHED;
        private final java.util.List<Object[]> tickets = new java.util.ArrayList<>();

        private EventBuilder(OrganizerProfile organizer, String title) {
            this.organizer = organizer;
            this.title = title;
        }

        public EventBuilder category(String slug) { this.category = slug; return this; }
        public EventBuilder city(String city) { this.city = city; return this; }
        public EventBuilder startsAt(Instant startsAt) { this.startsAt = startsAt; return this; }
        public EventBuilder endsAt(Instant endsAt) { this.endsAt = endsAt; return this; }
        public EventBuilder status(EventStatus status) { this.status = status; return this; }

        public EventBuilder ticket(String name, String price, int quantity) {
            tickets.add(new Object[] {name, price, quantity, TicketType.Status.ACTIVE});
            return this;
        }

        public EventBuilder inactiveTicket(String name, String price, int quantity) {
            tickets.add(new Object[] {name, price, quantity, TicketType.Status.INACTIVE});
            return this;
        }

        @Transactional
        public Event save() {
            Instant end = endsAt != null ? endsAt : startsAt.plus(3, ChronoUnit.HOURS);
            Event e = new Event(organizer, categories.findBySlug(category).orElseThrow(), title,
                    "Description of " + title, "Venue of " + title, "1 Test Street", city, startsAt, end);
            e.setStatus(status);
            for (Object[] t : tickets) {
                TicketType tt = new TicketType(e, (String) t[0], new BigDecimal((String) t[1]), "INR", (Integer) t[2],
                        null, null);
                tt.setStatus((TicketType.Status) t[3]);
                e.addTicketType(tt);
            }
            return events.saveAndFlush(e);
        }
    }
}
