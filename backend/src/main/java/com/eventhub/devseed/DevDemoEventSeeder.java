package com.eventhub.devseed;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.category.Category;
import com.eventhub.category.CategoryRepository;
import com.eventhub.event.Event;
import com.eventhub.event.EventRepository;
import com.eventhub.event.EventStatus;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.organizer.OrganizerProfileRepository;
import com.eventhub.ticket.TicketType;
import com.eventhub.user.EmailNormalizer;
import com.eventhub.user.User;
import com.eventhub.user.UserRepository;

/**
 * Local-development demo content so event discovery has something to show
 * before organizers can create events (Phase 4). Runs only when
 * {@code app.seed.enabled=true} and {@code app.seed.demo-events=true}, and only
 * if the seeded organizer has no profile yet. Includes non-published events
 * to make it visible that the public API hides them.
 */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "app.seed", name = {"enabled", "demo-events"}, havingValue = "true")
public class DevDemoEventSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDemoEventSeeder.class);

    private final DevAccountSeeder.SeedProperties props;
    private final UserRepository users;
    private final OrganizerProfileRepository profiles;
    private final CategoryRepository categories;
    private final EventRepository events;
    private final Clock clock;
    private final ZoneId zone;

    public DevDemoEventSeeder(DevAccountSeeder.SeedProperties props, UserRepository users,
            OrganizerProfileRepository profiles, CategoryRepository categories, EventRepository events,
            Clock clock, @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.props = props;
        this.users = users;
        this.profiles = profiles;
        this.categories = categories;
        this.events = events;
        this.clock = clock;
        this.zone = ZoneId.of(timezone);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.organizer() == null || props.organizer().email() == null || props.organizer().email().isBlank()) {
            log.info("Skipping demo events: no dev organizer configured");
            return;
        }
        User organizerUser = users.findByEmail(EmailNormalizer.normalize(props.organizer().email())).orElse(null);
        if (organizerUser == null) {
            log.info("Skipping demo events: dev organizer account does not exist");
            return;
        }
        if (profiles.findByUserId(organizerUser.getId()).isPresent()) {
            log.info("Demo events already seeded; leaving them unchanged");
            return;
        }

        OrganizerProfile org = new OrganizerProfile(organizerUser, "Nightfall Collective",
                "Independent promoters putting on gigs, meetups and workshops across India since 2019.",
                organizerUser.getEmail());
        org.setVerificationStatus(OrganizerProfile.VerificationStatus.VERIFIED);
        profiles.save(org);

        Map<String, Category> cat = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getSlug, Function.identity()));
        Seeder s = new Seeder(org, cat);

        s.event("music", "Indie Nights Live", "Bengaluru", "The Courtyard", "12 Church Street, Ashok Nagar", 4, "19:30", 4,
                EventStatus.PUBLISHED, "Three of the city's best indie bands on one stage, with a late-night DJ set to close.",
                t("Early Bird", "399", 80), t("General", "499", 250), t("VIP Lounge", "1499", 40));
        s.event("tech", "React & Spring Boot Meetup", "Hyderabad", "T-Hub", "IIIT Campus, Gachibowli", 9, "18:30", 3,
                EventStatus.PUBLISHED, "Lightning talks on building full-stack apps with React and Spring Boot, followed by networking.",
                t("Free Pass", "0", 150));
        s.event("comedy", "Stand-up Saturday", "Mumbai", "The Habitat", "Khar West, near Khar Station", 6, "21:00", 2,
                EventStatus.PUBLISHED, "Five comics, one mic and absolutely no topic off-limits. 18+ only.",
                t("Standard", "699", 120), t("Front Row", "999", 20));
        s.event("food-drink", "Street Food Carnival", "Delhi", "Jawaharlal Nehru Stadium", "Lodhi Road, Pragati Vihar", 12, "12:00", 9,
                EventStatus.PUBLISHED, "Forty stalls of regional street food, live music and a craft beer garden. Family-friendly.",
                t("Entry", "199", 2000), t("Tasting Pass", "799", 300));
        s.event("nightlife", "Neon Rooftop Party", "Bengaluru", "Skyline Terrace", "MG Road, Shanthala Nagar", 11, "21:00", 5,
                EventStatus.PUBLISHED, "Glow paint, neon lights and house music until late on the city's best rooftop.",
                t("Entry", "999", 300));
        s.event("workshops", "Pottery for Beginners", "Pune", "Clay Studio", "Koregaon Park Lane 6", 15, "10:00", 3,
                EventStatus.PUBLISHED, "Learn to centre, throw and glaze your first bowl. All materials included; take your piece home.",
                t("Seat", "1800", 16));
        s.event("arts", "Contemporary Art Walk", "Mumbai", "Kala Ghoda", "Kala Ghoda Arts Precinct, Fort", 18, "16:00", 3,
                EventStatus.PUBLISHED, "A guided walk through five galleries with the curators, ending with drinks.",
                t("Free Walk", "0", 60), t("Walk + Drinks", "650", 40));
        s.event("sports", "Sunrise 10K Run", "Chennai", "Marina Beach", "Kamarajar Salai, Triplicane", 21, "05:30", 3,
                EventStatus.PUBLISHED, "A flat, fast 10K along the coast at sunrise. Timing chip, medal and breakfast included.",
                t("Runner", "900", 1500));
        s.event("music", "Classical Evening: Ragas at Dusk", "Chennai", "Music Academy", "TTK Road, Royapettah", 25, "18:00", 3,
                EventStatus.PUBLISHED, "An evening of Carnatic music from rising young artists.",
                t("Balcony", "300", 400), t("Stalls", "750", 300));
        s.event("tech", "AI Builders Hack Night", "Bengaluru", "Koramangala Social", "80 Feet Road, Koramangala", 30, "17:00", 6,
                EventStatus.PUBLISHED, "Bring a laptop and an idea. Five hours to build something with AI; prizes for the best demos.",
                t("Hacker", "0", 120));
        s.event("comedy", "Improv Jam", "Pune", "The Loft", "FC Road, Shivajinagar", 33, "20:00", 2,
                EventStatus.PUBLISHED, "Unscripted, audience-driven comedy. Shout a suggestion and watch it come to life.",
                t("Entry", "350", 90));
        s.event("food-drink", "Coffee Cupping Masterclass", "Bengaluru", "Third Wave Roastery", "Indiranagar 100 Feet Road", 40, "11:00", 2,
                EventStatus.PUBLISHED, "Taste six single-origin Indian coffees side by side with a Q-grader.",
                t("Seat", "1200", 20));

        // Not public: proves that discovery only shows PUBLISHED, upcoming events.
        s.event("music", "Unannounced Secret Gig (draft)", "Bengaluru", "TBA", "TBA", 45, "20:00", 3,
                EventStatus.DRAFT, "Draft event that must not appear publicly.", t("General", "499", 100));
        s.event("tech", "Cloud Summit (pending approval)", "Hyderabad", "HICC", "Novotel, Kondapur", 50, "09:00", 8,
                EventStatus.PENDING_APPROVAL, "Awaiting admin approval; must not appear publicly.", t("Delegate", "2500", 500));
        s.event("nightlife", "Rejected Warehouse Rave", "Mumbai", "Warehouse 9", "Lower Parel", 14, "22:00", 6,
                EventStatus.REJECTED, "Rejected by moderation; must not appear publicly.", t("Entry", "1500", 300));
        s.event("arts", "Last Month's Photo Exhibition", "Delhi", "Habitat Centre", "Lodhi Road", -20, "11:00", 8,
                EventStatus.PUBLISHED, "A published event in the past; hidden from upcoming listings.", t("Entry", "0", 200));

        log.info("Seeded demo organizer '{}' with {} events", org.getDisplayName(), s.count);
    }

    private record TicketSpec(String name, String price, int quantity) {
    }

    private static TicketSpec t(String name, String price, int quantity) {
        return new TicketSpec(name, price, quantity);
    }

    private final class Seeder {
        private final OrganizerProfile org;
        private final Map<String, Category> categories;
        int count;

        Seeder(OrganizerProfile org, Map<String, Category> categories) {
            this.org = org;
            this.categories = categories;
        }

        void event(String category, String title, String city, String venue, String address, int daysFromToday,
                String startTime, int durationHours, EventStatus status, String description, TicketSpec... tickets) {
            Instant starts = LocalDate.now(clock.withZone(zone)).plusDays(daysFromToday)
                    .atTime(LocalTime.parse(startTime)).atZone(zone).toInstant();
            Instant ends = starts.plus(durationHours, ChronoUnit.HOURS);
            Event e = new Event(org, categories.get(category), title, description, venue, address, city, starts, ends);
            e.setStatus(status);
            for (TicketSpec spec : tickets) {
                e.addTicketType(new TicketType(e, spec.name(), new BigDecimal(spec.price()), "INR", spec.quantity(),
                        null, starts));
            }
            events.save(e);
            count++;
        }
    }
}
