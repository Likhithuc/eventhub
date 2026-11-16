package com.eventhub.event;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.support.IntegrationTest;
import com.eventhub.support.TestEvents;

/**
 * Public discovery API. Every request here is anonymous (no token), proving
 * discovery is open to visitors. Each test uses its own unique city so tests
 * do not see each other's events.
 */
@IntegrationTest
class EventDiscoveryIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired MockMvc mvc;
    @Autowired TestEvents testEvents;

    OrganizerProfile org;
    String city;

    @BeforeEach
    void setUp() {
        org = testEvents.organizer("Test Promoters");
        city = TestEvents.uniqueCity();
    }

    @Test
    void categoriesAreListedPubliclyInDisplayOrder() throws Exception {
        mvc.perform(get("/api/v1/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(8))
            .andExpect(jsonPath("$[0].slug").value("music"))
            .andExpect(jsonPath("$[0].name").value("Music"))
            .andExpect(jsonPath("$[*].slug", hasItem("food-drink")));
    }

    @Test
    void onlyPublishedUpcomingEventsAreListedSoonestFirst() throws Exception {
        testEvents.event(org, "Later published").city(city).startsAt(TestEvents.daysFromNow(10)).save();
        testEvents.event(org, "Sooner published").city(city).startsAt(TestEvents.daysFromNow(2)).save();
        // Started an hour ago and still running: still listed.
        testEvents.event(org, "Happening now").city(city)
                .startsAt(Instant.now().minus(1, ChronoUnit.HOURS)).endsAt(Instant.now().plus(2, ChronoUnit.HOURS)).save();
        for (EventStatus hidden : new EventStatus[] {EventStatus.DRAFT, EventStatus.PENDING_APPROVAL,
                EventStatus.REJECTED, EventStatus.CANCELLED, EventStatus.COMPLETED}) {
            testEvents.event(org, "Hidden " + hidden).city(city).status(hidden).save();
        }
        testEvents.event(org, "Already over").city(city)
                .startsAt(TestEvents.daysFromNow(-3)).endsAt(TestEvents.daysFromNow(-3).plus(2, ChronoUnit.HOURS)).save();

        mvc.perform(get("/api/v1/events").param("city", city))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[*].title", contains("Happening now", "Sooner published", "Later published")));
    }

    @Test
    void filtersByCityCaseInsensitively() throws Exception {
        testEvents.event(org, "In my city").city(city).save();
        testEvents.event(org, "Elsewhere").city(TestEvents.uniqueCity()).save();

        mvc.perform(get("/api/v1/events").param("city", city.toUpperCase()))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("In my city"))
            .andExpect(jsonPath("$.content[0].city").value(city));
    }

    @Test
    void filtersByCategorySlug() throws Exception {
        testEvents.event(org, "Gig").city(city).category("music").save();
        testEvents.event(org, "Meetup").city(city).category("tech").save();

        mvc.perform(get("/api/v1/events").param("city", city).param("category", "tech"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Meetup"))
            .andExpect(jsonPath("$.content[0].category.slug").value("tech"))
            .andExpect(jsonPath("$.content[0].category.name").value("Tech"));
    }

    @Test
    void searchesTitleCaseInsensitivelyAndTreatsWildcardsLiterally() throws Exception {
        testEvents.event(org, "Jazz Under The Stars").city(city).save();
        testEvents.event(org, "Rock Night").city(city).save();
        testEvents.event(org, "100% Techno").city(city).save();

        mvc.perform(get("/api/v1/events").param("city", city).param("q", "  jAzZ "))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Jazz Under The Stars"));
        // '%' must match a literal percent sign, not "anything".
        mvc.perform(get("/api/v1/events").param("city", city).param("q", "%"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("100% Techno"));
    }

    @Test
    void filtersByInclusiveLocalDateRange() throws Exception {
        LocalDate day10 = LocalDate.now(IST).plusDays(10);
        LocalDate day20 = LocalDate.now(IST).plusDays(20);
        // 23:30 IST is still "day 10" locally even though it is 18:00 UTC.
        testEvents.event(org, "Late on day 10").city(city)
                .startsAt(day10.atTime(LocalTime.of(23, 30)).atZone(IST).toInstant()).save();
        testEvents.event(org, "Day 20").city(city).startsAt(day20.atTime(LocalTime.NOON).atZone(IST).toInstant()).save();

        mvc.perform(get("/api/v1/events").param("city", city).param("from", day10.toString()).param("to", day10.toString()))
            .andExpect(jsonPath("$.content[*].title", contains("Late on day 10")));
        mvc.perform(get("/api/v1/events").param("city", city).param("from", day10.plusDays(1).toString()))
            .andExpect(jsonPath("$.content[*].title", contains("Day 20")));
        mvc.perform(get("/api/v1/events").param("city", city).param("to", day20.toString()))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void rejectsInvalidFilters() throws Exception {
        mvc.perform(get("/api/v1/events").param("from", "2030-02-10").param("to", "2030-02-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("'from' must be on or before 'to'"));
        mvc.perform(get("/api/v1/events").param("from", "not-a-date"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/events").param("q", "x".repeat(101)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void paginatesAndCapsPageSize() throws Exception {
        for (int i = 1; i <= 3; i++) {
            testEvents.event(org, "Event " + i).city(city).startsAt(TestEvents.daysFromNow(i)).save();
        }

        mvc.perform(get("/api/v1/events").param("city", city).param("size", "2").param("page", "1"))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.content[*].title", contains("Event 3")));
        mvc.perform(get("/api/v1/events").param("city", city).param("size", "500"))
            .andExpect(jsonPath("$.size").value(EventDiscoveryService.MAX_PAGE_SIZE));
    }

    @Test
    void listShowsPriceRangeFromActiveTicketTypes() throws Exception {
        testEvents.event(org, "Mixed").city(city).startsAt(TestEvents.daysFromNow(1))
                .ticket("Free", "0", 10).ticket("VIP", "1500", 5).inactiveTicket("Retired", "9999", 1).save();
        testEvents.event(org, "All free").city(city).startsAt(TestEvents.daysFromNow(2)).ticket("Entry", "0", 10).save();
        testEvents.event(org, "No tickets yet").city(city).startsAt(TestEvents.daysFromNow(3)).save();

        mvc.perform(get("/api/v1/events").param("city", city))
            .andExpect(jsonPath("$.content[0].price.min").value(0.0))
            .andExpect(jsonPath("$.content[0].price.max").value(1500.0))
            .andExpect(jsonPath("$.content[0].price.free").value(false))
            .andExpect(jsonPath("$.content[0].price.currency").value("INR"))
            .andExpect(jsonPath("$.content[1].price.free").value(true))
            .andExpect(jsonPath("$.content[2].price.min").doesNotExist())
            .andExpect(jsonPath("$.content[0].organizerName").value("Test Promoters"));
    }

    @Test
    void detailsOfPublishedEventIncludeOrganizerAndActiveTicketsCheapestFirst() throws Exception {
        Event event = testEvents.event(org, "Big Show").city(city).category("comedy")
                .ticket("VIP", "999", 20).ticket("General", "499", 100).inactiveTicket("Old", "1", 1).save();

        mvc.perform(get("/api/v1/events/{id}", event.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Big Show"))
            .andExpect(jsonPath("$.description").value("Description of Big Show"))
            .andExpect(jsonPath("$.address").value("1 Test Street"))
            .andExpect(jsonPath("$.category.slug").value("comedy"))
            .andExpect(jsonPath("$.ended").value(false))
            .andExpect(jsonPath("$.organizer.displayName").value("Test Promoters"))
            .andExpect(jsonPath("$.organizer.verified").value(false))
            .andExpect(jsonPath("$.ticketTypes[*].name", contains("General", "VIP")))
            .andExpect(jsonPath("$.ticketTypes[0].available").value(100))
            .andExpect(jsonPath("$.ticketTypes[0].onSale").value(true))
            .andExpect(jsonPath("$.price.min").value(499.0))
            .andExpect(jsonPath("$.organizer.contactEmail").doesNotExist());
    }

    @Test
    void nonPublishedEventsReturn404SameAsMissing() throws Exception {
        for (EventStatus hidden : new EventStatus[] {EventStatus.DRAFT, EventStatus.PENDING_APPROVAL,
                EventStatus.REJECTED, EventStatus.CANCELLED}) {
            Event e = testEvents.event(org, "Secret " + hidden).city(city).status(hidden).save();
            mvc.perform(get("/api/v1/events/{id}", e.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Event not found"));
        }
        mvc.perform(get("/api/v1/events/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Event not found"));
        mvc.perform(get("/api/v1/events/not-a-uuid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void pastPublishedEventIsViewableButMarkedEndedAndNotOnSale() throws Exception {
        Event past = testEvents.event(org, "Last week").city(city)
                .startsAt(TestEvents.daysFromNow(-7)).ticket("Entry", "100", 10).save();

        mvc.perform(get("/api/v1/events/{id}", past.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ended").value(true))
            .andExpect(jsonPath("$.ticketTypes[0].onSale").value(false));
    }

    @Test
    void citiesListOnlyIncludesCitiesWithUpcomingPublishedEvents() throws Exception {
        String draftOnlyCity = TestEvents.uniqueCity();
        testEvents.event(org, "Public").city(city).save();
        testEvents.event(org, "Draft").city(draftOnlyCity).status(EventStatus.DRAFT).save();

        mvc.perform(get("/api/v1/events/cities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasItem(city)))
            .andExpect(jsonPath("$", not(hasItem(draftOnlyCity))));
    }
}
