package com.eventhub.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.eventhub.event.Event;
import com.eventhub.event.EventRepository;
import com.eventhub.event.EventStatus;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.support.IntegrationTest;
import com.eventhub.support.TestEvents;
import com.eventhub.support.TestUsers;
import com.eventhub.user.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * Phase 6 "done when": users can book available tickets and the system never
 * sells more tickets than configured capacity, even under concurrent requests.
 */
@IntegrationTest
class BookingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestEvents testEvents;
    @Autowired EventRepository events;
    @Autowired JdbcTemplate jdbc;

    OrganizerProfile org;
    String orgToken;
    String attendeeToken;

    @BeforeEach
    void setUp() throws Exception {
        org = testEvents.organizer("Booking Promoters");
        orgToken = TestUsers.accessToken(mvc, org.getUser());
        attendeeToken = newAttendeeToken();
    }

    private String newAttendeeToken() throws Exception {
        return TestUsers.accessToken(mvc, testUsers.create("attendee", EnumSet.of(Role.ATTENDEE)));
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private Event freeEvent(int quantity) {
        return testEvents.event(org, "Free gig " + UUID.randomUUID()).city(TestEvents.uniqueCity())
                .ticket("General", "0", quantity).save();
    }

    private static String ticketId(Event e, String name) {
        return e.getTicketTypes().stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow().getId().toString();
    }

    private static String body(Event e, String ticketTypeId, int qty) {
        return "{\"eventId\":\"" + e.getId() + "\",\"items\":[{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":" + qty + "}]}";
    }

    private ResultActions book(String token, Event e, String ticketTypeId, int qty, String key) throws Exception {
        var req = as(token, post("/api/v1/bookings")).content(body(e, ticketTypeId, qty));
        if (key != null) req.header("Idempotency-Key", key);
        return mvc.perform(req);
    }

    private int sold(String ticketTypeId) {
        return jdbc.queryForObject("select sold_quantity from ticket_types where id = ?::uuid", Integer.class, ticketTypeId);
    }

    private long confirmedBookings(Event e) {
        return jdbc.queryForObject("select count(*) from bookings where event_id = ? and status = 'CONFIRMED'", Long.class, e.getId());
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void bookingFreeTicketsConfirmsAndReducesAvailability() throws Exception {
        Event e = freeEvent(50);
        String general = ticketId(e, "General");

        String json = book(attendeeToken, e, general, 3, null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.reference").value(matchesPattern("EH-[A-Z2-9]{8}")))
            .andExpect(jsonPath("$.ticketCount").value(3))
            .andExpect(jsonPath("$.totalAmount").value(0.0))
            .andExpect(jsonPath("$.items[0].name").value("General"))
            .andExpect(jsonPath("$.event.id").value(e.getId().toString()))
            .andExpect(jsonPath("$.canCancel").value(true))
            .andReturn().getResponse().getContentAsString();

        assertThat(sold(general)).isEqualTo(3);
        mvc.perform(get("/api/v1/events/" + e.getId()))
            .andExpect(jsonPath("$.ticketTypes[0].available").value(47));

        String id = JsonPath.read(json, "$.id");
        mvc.perform(as(attendeeToken, get("/api/v1/bookings/me")))
            .andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isNotEmpty());
    }

    // ---------------------------------------------------------------- validation & rules

    @Test
    void rejectsInvalidQuantities() throws Exception {
        Event e = freeEvent(50);
        String general = ticketId(e, "General");
        book(attendeeToken, e, general, 0, null).andExpect(status().isBadRequest());
        book(attendeeToken, e, general, -2, null).andExpect(status().isBadRequest());
        book(attendeeToken, e, general, 11, null).andExpect(status().isBadRequest());
        mvc.perform(as(attendeeToken, post("/api/v1/bookings")).content("{\"eventId\":\"" + e.getId() + "\",\"items\":[]}"))
            .andExpect(status().isBadRequest());
        // Same ticket type twice.
        mvc.perform(as(attendeeToken, post("/api/v1/bookings")).content("{\"eventId\":\"" + e.getId() + "\",\"items\":["
                + "{\"ticketTypeId\":\"" + general + "\",\"quantity\":1},{\"ticketTypeId\":\"" + general + "\",\"quantity\":1}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Each ticket type may appear only once"));
        assertThat(sold(general)).isZero();
    }

    @Test
    void rejectsMoreThanTenTicketsAcrossTypes() throws Exception {
        Event e = testEvents.event(org, "Two types").ticket("A", "0", 50).ticket("B", "0", 50).save();
        mvc.perform(as(attendeeToken, post("/api/v1/bookings")).content("{\"eventId\":\"" + e.getId() + "\",\"items\":["
                + "{\"ticketTypeId\":\"" + ticketId(e, "A") + "\",\"quantity\":6},"
                + "{\"ticketTypeId\":\"" + ticketId(e, "B") + "\",\"quantity\":5}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("You can book at most 10 tickets at a time"));
    }

    @Test
    void rejectsQuantityAboveRemainingInventory() throws Exception {
        Event e = freeEvent(5);
        String general = ticketId(e, "General");
        book(attendeeToken, e, general, 4, null).andExpect(status().isCreated());
        book(newAttendeeToken(), e, general, 2, null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Only 1 'General' ticket is left"));
        book(newAttendeeToken(), e, general, 1, null).andExpect(status().isCreated());
        book(newAttendeeToken(), e, general, 1, null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("'General' is sold out"));
        assertThat(sold(general)).isEqualTo(5);
        mvc.perform(get("/api/v1/events/" + e.getId()))
            .andExpect(jsonPath("$.ticketTypes[0].soldOut").value(true))
            .andExpect(jsonPath("$.ticketTypes[0].onSale").value(false));
    }

    @Test
    void rejectsPaidTicketsUntilPaymentsExist() throws Exception {
        Event e = testEvents.event(org, "Paid gig").ticket("VIP", "499", 10).save();
        book(attendeeToken, e, ticketId(e, "VIP"), 1, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("paid ticket")));
        assertThat(sold(ticketId(e, "VIP"))).isZero();
    }

    @Test
    void rejectsNonPublishedStartedAndUnknownEvents() throws Exception {
        for (EventStatus hidden : new EventStatus[] {EventStatus.DRAFT, EventStatus.PENDING_APPROVAL, EventStatus.CANCELLED}) {
            Event e = testEvents.event(org, "Hidden " + hidden).status(hidden).ticket("General", "0", 10).save();
            book(attendeeToken, e, ticketId(e, "General"), 1, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Event not found"));
        }
        Event started = testEvents.event(org, "Already started").startsAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .endsAt(Instant.now().plus(2, ChronoUnit.HOURS)).ticket("General", "0", 10).save();
        book(attendeeToken, started, ticketId(started, "General"), 1, null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Bookings have closed for this event"));

        Event other = freeEvent(10);
        Event e = freeEvent(10);
        book(attendeeToken, e, ticketId(other, "General"), 1, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("One or more ticket types don't belong to this event"));
    }

    @Test
    void rejectsTicketsOutsideTheirSalesWindow() throws Exception {
        Event e = freeEvent(10);
        String general = ticketId(e, "General");
        jdbc.update("update ticket_types set sales_start_at = now() + interval '2 days' where id = ?::uuid", general);
        book(attendeeToken, e, general, 1, null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("'General' tickets are not on sale right now"));
        jdbc.update("update ticket_types set sales_start_at = null, sales_end_at = now() - interval '1 hour' where id = ?::uuid", general);
        book(attendeeToken, e, general, 1, null).andExpect(status().isConflict());
        jdbc.update("update ticket_types set sales_end_at = null, status = 'INACTIVE' where id = ?::uuid", general);
        book(attendeeToken, e, general, 1, null).andExpect(status().isConflict());
    }

    @Test
    void requiresAuthentication() throws Exception {
        Event e = freeEvent(10);
        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(body(e, ticketId(e, "General"), 1)))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/bookings/me")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- concurrency: never oversell

    @Test
    void concurrentRequestsNeverOversellTheLastTickets() throws Exception {
        Event e = freeEvent(5);
        String general = ticketId(e, "General");
        int buyers = 12;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < buyers; i++) tokens.add(newAttendeeToken());

        List<Integer> statuses = runConcurrently(tokens.stream()
                .map(t -> (Callable<Integer>) () -> book(t, e, general, 1, null).andReturn().getResponse().getStatus())
                .toList());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(5);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(buyers - 5);
        assertThat(sold(general)).isEqualTo(5);
        assertThat(confirmedBookings(e)).isEqualTo(5);
    }

    @Test
    void twoSimultaneousLargeRequestsCannotBothTakeTheLastSeven() throws Exception {
        // The plan's example: 10 tickets left, two users try to book 7 at the same time.
        Event e = freeEvent(10);
        String general = ticketId(e, "General");
        String a = newAttendeeToken();
        String b = newAttendeeToken();

        List<Integer> statuses = runConcurrently(List.of(
                () -> book(a, e, general, 7, null).andReturn().getResponse().getStatus(),
                () -> book(b, e, general, 7, null).andReturn().getResponse().getStatus()));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(sold(general)).isEqualTo(7);
    }

    // ---------------------------------------------------------------- idempotency: no duplicates

    @Test
    void retryWithSameIdempotencyKeyReturnsTheOriginalBooking() throws Exception {
        Event e = freeEvent(10);
        String general = ticketId(e, "General");
        String key = UUID.randomUUID().toString();

        String first = book(attendeeToken, e, general, 2, key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = book(attendeeToken, e, general, 2, key).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(sold(general)).isEqualTo(2);
        assertThat(confirmedBookings(e)).isEqualTo(1);

        book(attendeeToken, e, general, 3, key)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("This Idempotency-Key was already used for a different booking"));
        // Keys are per user: someone else may use the same key.
        book(newAttendeeToken(), e, general, 1, key).andExpect(status().isCreated());
    }

    @Test
    void simultaneousDuplicateRequestsCreateOnlyOneBooking() throws Exception {
        Event e = freeEvent(20);
        String general = ticketId(e, "General");
        String key = UUID.randomUUID().toString();

        List<Integer> statuses = runConcurrently(List.of(
                () -> book(attendeeToken, e, general, 2, key).andReturn().getResponse().getStatus(),
                () -> book(attendeeToken, e, general, 2, key).andReturn().getResponse().getStatus(),
                () -> book(attendeeToken, e, general, 2, key).andReturn().getResponse().getStatus()));

        assertThat(statuses).containsExactlyInAnyOrder(201, 200, 200);
        assertThat(sold(general)).isEqualTo(2);
        assertThat(confirmedBookings(e)).isEqualTo(1);
    }

    @Test
    void rejectsMalformedIdempotencyKey() throws Exception {
        Event e = freeEvent(5);
        book(attendeeToken, e, ticketId(e, "General"), 1, "has spaces!").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- viewing & cancelling

    @Test
    void usersOnlySeeTheirOwnBookings() throws Exception {
        Event e = freeEvent(10);
        String id = JsonPath.read(book(attendeeToken, e, ticketId(e, "General"), 1, null)
                .andReturn().getResponse().getContentAsString(), "$.id");
        String stranger = newAttendeeToken();

        mvc.perform(as(attendeeToken, get("/api/v1/bookings/" + id))).andExpect(status().isOk());
        mvc.perform(as(stranger, get("/api/v1/bookings/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(stranger, post("/api/v1/bookings/" + id + "/cancel"))).andExpect(status().isNotFound());
        mvc.perform(as(stranger, get("/api/v1/bookings/me")))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void cancellingReturnsTicketsToInventoryAndMovesToCancelledList() throws Exception {
        Event e = freeEvent(5);
        String general = ticketId(e, "General");
        String id = JsonPath.read(book(attendeeToken, e, general, 5, null)
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(sold(general)).isEqualTo(5);

        mvc.perform(as(attendeeToken, post("/api/v1/bookings/" + id + "/cancel")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.canCancel").value(false));
        assertThat(sold(general)).isZero();

        mvc.perform(as(attendeeToken, post("/api/v1/bookings/" + id + "/cancel"))).andExpect(status().isConflict());
        mvc.perform(as(attendeeToken, get("/api/v1/bookings/me")).param("scope", "CANCELLED"))
            .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(as(attendeeToken, get("/api/v1/bookings/me")).param("scope", "UPCOMING"))
            .andExpect(jsonPath("$.content[?(@.id == '" + id + "')]").isEmpty());

        // The released tickets can be booked again.
        book(newAttendeeToken(), e, general, 5, null).andExpect(status().isCreated());
    }

    @Test
    void cancellationIsRefusedAfterTheEventsCutoff() throws Exception {
        Event e = testEvents.event(org, "Soon").startsAt(Instant.now().plus(5, ChronoUnit.HOURS))
                .ticket("General", "0", 10).save(); // default cutoff: 24 h before start
        String id = JsonPath.read(book(attendeeToken, e, ticketId(e, "General"), 1, null)
                .andExpect(jsonPath("$.canCancel").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(as(attendeeToken, post("/api/v1/bookings/" + id + "/cancel")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Cancellations closed 24 hours before the event starts"));

        jdbc.update("update events set cancellation_cutoff_hours = 0 where id = ?", e.getId());
        mvc.perform(as(attendeeToken, post("/api/v1/bookings/" + id + "/cancel"))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- organizer side

    @Test
    void organizerSeesAttendeesOfOwnEventOnly() throws Exception {
        Event e = freeEvent(10);
        book(attendeeToken, e, ticketId(e, "General"), 2, null).andExpect(status().isCreated());

        mvc.perform(as(orgToken, get("/api/v1/organizer/events/" + e.getId() + "/bookings")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.confirmedBookings").value(1))
            .andExpect(jsonPath("$.summary.ticketsSold").value(2))
            .andExpect(jsonPath("$.summary.capacity").value(10))
            .andExpect(jsonPath("$.summary.ticketTypes[0].sold").value(2))
            .andExpect(jsonPath("$.bookings.content[0].ticketCount").value(2))
            .andExpect(jsonPath("$.bookings.content[0].items[0]").value("2 × General"))
            .andExpect(jsonPath("$.bookings.content[0].attendeeEmail").isNotEmpty());

        String otherOrg = TestUsers.accessToken(mvc, testEvents.organizer("Someone Else").getUser());
        mvc.perform(as(otherOrg, get("/api/v1/organizer/events/" + e.getId() + "/bookings"))).andExpect(status().isNotFound());
        mvc.perform(as(attendeeToken, get("/api/v1/organizer/events/" + e.getId() + "/bookings"))).andExpect(status().isForbidden());
    }

    @Test
    void cancellingTheEventCancelsItsBookings() throws Exception {
        Event e = freeEvent(10);
        String general = ticketId(e, "General");
        String id = JsonPath.read(book(attendeeToken, e, general, 3, null)
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(as(orgToken, post("/api/v1/organizer/events/" + e.getId() + "/cancel"))).andExpect(status().isOk());

        mvc.perform(as(attendeeToken, get("/api/v1/bookings/" + id)))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancellationReason").value("Event cancelled by the organizer"));
        assertThat(sold(general)).isZero();
    }

    @Test
    void ticketQuantityCannotDropBelowSoldAndSoldTicketTypesCannotBeDeleted() throws Exception {
        Event e = freeEvent(10);
        String general = ticketId(e, "General");
        book(attendeeToken, e, general, 4, null).andExpect(status().isCreated());
        // e.g. admin unpublished it, so the organizer can edit again
        jdbc.update("update events set status = 'REJECTED' where id = ?", e.getId());

        mvc.perform(as(orgToken, put("/api/v1/organizer/events/" + e.getId() + "/ticket-types/" + general))
                .content("{\"name\":\"General\",\"price\":0,\"totalQuantity\":3}"))
            .andExpect(status().isConflict());
        mvc.perform(as(orgToken, put("/api/v1/organizer/events/" + e.getId() + "/ticket-types/" + general))
                .content("{\"name\":\"General\",\"price\":0,\"totalQuantity\":4}"))
            .andExpect(status().isOk());
        mvc.perform(as(orgToken,
                delete("/api/v1/organizer/events/" + e.getId() + "/ticket-types/" + general)))
            .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- helpers

    /** Starts all tasks at the same instant and returns their results. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) results.add(f.get(60, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
