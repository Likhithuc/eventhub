package com.eventhub.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
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
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.support.IntegrationTest;
import com.eventhub.support.RecordingMailSender;
import com.eventhub.support.TestEvents;
import com.eventhub.support.TestUsers;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.jayway.jsonpath.JsonPath;

import jakarta.mail.internet.MimeMessage;

/**
 * Phase 7 "done when": an organizer can validate a ticket once and see its check-in status.
 * Also covers per-ticket passes and the booking confirmation email.
 */
@IntegrationTest
class CheckInIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestEvents testEvents;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecordingMailSender mail;

    OrganizerProfile org;
    String orgToken;
    User attendee;
    String attendeeToken;

    @BeforeEach
    void setUp() throws Exception {
        org = testEvents.organizer("Door Staff Inc");
        orgToken = TestUsers.accessToken(mvc, org.getUser());
        attendee = testUsers.create("guest", EnumSet.of(Role.ATTENDEE));
        attendeeToken = TestUsers.accessToken(mvc, attendee);
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** Free event starting in 2 hours, so check-in is open. */
    private Event soonEvent() {
        return testEvents.event(org, "Tonight " + UUID.randomUUID()).startsAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .ticket("General", "0", 50).save();
    }

    private String book(Event e, int qty) throws Exception {
        String ticketTypeId = e.getTicketTypes().getFirst().getId().toString();
        return mvc.perform(as(attendeeToken, post("/api/v1/bookings")).content(
                        "{\"eventId\":\"" + e.getId() + "\",\"items\":[{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":" + qty + "}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions checkIn(String token, Event e, String code) throws Exception {
        return mvc.perform(as(token, post("/api/v1/organizer/events/" + e.getId() + "/check-ins"))
                .content("{\"code\":\"" + code + "\"}"));
    }

    // ---------------------------------------------------------------- passes

    @Test
    void eachSeatGetsItsOwnUnguessablePass() throws Exception {
        Event e = soonEvent();
        String booking = book(e, 3);

        List<String> codes = JsonPath.read(booking, "$.tickets[*].code");
        List<String> qrs = JsonPath.read(booking, "$.tickets[*].qr");
        assertThat(codes).hasSize(3).allMatch(c -> c.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertThat(new HashSet<>(codes)).hasSize(3);
        assertThat(qrs).hasSize(3).allMatch(q -> q.matches("EH1\\.[0-9a-f]{32}"));
        assertThat(new HashSet<>(qrs)).hasSize(3);
        // No personal data in the QR payload.
        assertThat(qrs).noneMatch(q -> q.contains(attendee.getEmail()) || q.contains(attendee.getName()));
        assertThat((List<String>) JsonPath.read(booking, "$.tickets[*].status")).containsOnly("VALID");

        // The organizer's attendee list never exposes the QR secret.
        String list = mvc.perform(as(orgToken, get("/api/v1/organizer/events/" + e.getId() + "/bookings")))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(qrs.getFirst().substring(4));
    }

    // ---------------------------------------------------------------- check-in

    @Test
    void ticketValidatesOnceAndStatusIsVisible() throws Exception {
        Event e = soonEvent();
        String booking = book(e, 2);
        String code = JsonPath.read(booking, "$.tickets[0].code");
        String bookingId = JsonPath.read(booking, "$.id");

        checkIn(orgToken, e, code)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("CHECKED_IN"))
            .andExpect(jsonPath("$.ticket.attendeeName").value(attendee.getName()))
            .andExpect(jsonPath("$.ticket.ticketType").value("General"))
            .andExpect(jsonPath("$.checkedInBy").value(org.getUser().getName()));

        // Second scan is refused and says when it was first used.
        checkIn(orgToken, e, code)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.result").value("ALREADY_CHECKED_IN"))
            .andExpect(jsonPath("$.checkedInAt").isNotEmpty());

        // The other person in the same booking is still independent.
        checkIn(orgToken, e, JsonPath.read(booking, "$.tickets[1].code")).andExpect(status().isOk());

        // Status is visible to the organizer and to the attendee.
        mvc.perform(as(orgToken, get("/api/v1/organizer/events/" + e.getId() + "/check-ins/summary")))
            .andExpect(jsonPath("$.validTickets").value(2))
            .andExpect(jsonPath("$.checkedIn").value(2))
            .andExpect(jsonPath("$.open").value(true))
            .andExpect(jsonPath("$.recent.length()").value(2));
        mvc.perform(as(attendeeToken, get("/api/v1/bookings/" + bookingId)))
            .andExpect(jsonPath("$.tickets[0].status").value("CHECKED_IN"))
            .andExpect(jsonPath("$.tickets[0].checkedInAt").isNotEmpty())
            .andExpect(jsonPath("$.canCancel").value(false));
        mvc.perform(as(orgToken, get("/api/v1/organizer/events/" + e.getId() + "/bookings")))
            .andExpect(jsonPath("$.bookings.content[0].checkedIn").value(2));
    }

    @Test
    void acceptsQrPayloadAndForgivingManualCodes() throws Exception {
        Event e = soonEvent();
        String booking = book(e, 3);
        checkIn(orgToken, e, JsonPath.read(booking, "$.tickets[0].qr")).andExpect(jsonPath("$.result").value("CHECKED_IN"));
        String code = JsonPath.read(booking, "$.tickets[1].code");
        checkIn(orgToken, e, "  " + code.toLowerCase().replace("-", " ") + " ").andExpect(jsonPath("$.result").value("CHECKED_IN"));
        String qr = JsonPath.read(booking, "$.tickets[2].qr");
        checkIn(orgToken, e, qr.substring(4).toUpperCase()).andExpect(jsonPath("$.result").value("CHECKED_IN"));
    }

    @Test
    void unknownCodesAndOtherEventsTicketsAreNotFound() throws Exception {
        Event e = soonEvent();
        Event other = soonEvent();
        String otherBooking = book(other, 1);

        checkIn(orgToken, e, "ZZZZ-ZZZZ").andExpect(status().isNotFound()).andExpect(jsonPath("$.result").value("NOT_FOUND"));
        checkIn(orgToken, e, "garbage").andExpect(status().isNotFound());
        checkIn(orgToken, e, JsonPath.read(otherBooking, "$.tickets[0].code")).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheEventsOrganizerCanCheckIn() throws Exception {
        Event e = soonEvent();
        String code = JsonPath.read(book(e, 1), "$.tickets[0].code");
        String otherOrg = TestUsers.accessToken(mvc, testEvents.organizer("Rival Promoters").getUser());

        checkIn(otherOrg, e, code).andExpect(status().isNotFound());
        checkIn(attendeeToken, e, code).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/organizer/events/" + e.getId() + "/check-ins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(status().isUnauthorized());
        // Still not checked in.
        checkIn(orgToken, e, code).andExpect(jsonPath("$.result").value("CHECKED_IN"));
    }

    @Test
    void cancelledTicketsAreRefused() throws Exception {
        Event e = testEvents.event(org, "Cancellable").startsAt(Instant.now().plus(3, ChronoUnit.HOURS))
                .ticket("General", "0", 10).save();
        jdbc.update("update events set cancellation_cutoff_hours = 0 where id = ?", e.getId());
        String booking = book(e, 1);
        mvc.perform(as(attendeeToken, post("/api/v1/bookings/" + JsonPath.read(booking, "$.id") + "/cancel")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tickets[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.tickets[0].qr").doesNotExist());

        checkIn(orgToken, e, JsonPath.read(booking, "$.tickets[0].code"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.result").value("CANCELLED"));
    }

    @Test
    void bookingCannotBeCancelledAfterCheckIn() throws Exception {
        Event e = testEvents.event(org, "No take-backs").startsAt(Instant.now().plus(3, ChronoUnit.HOURS))
                .ticket("General", "0", 10).save();
        jdbc.update("update events set cancellation_cutoff_hours = 0 where id = ?", e.getId());
        String booking = book(e, 2);
        checkIn(orgToken, e, JsonPath.read(booking, "$.tickets[0].code")).andExpect(status().isOk());

        mvc.perform(as(attendeeToken, post("/api/v1/bookings/" + JsonPath.read(booking, "$.id") + "/cancel")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Tickets from this booking have already been checked in"));
    }

    @Test
    void checkInOnlyOpensADayBeforeTheEvent() throws Exception {
        Event later = testEvents.event(org, "Next week").startsAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .ticket("General", "0", 10).save();
        checkIn(orgToken, later, JsonPath.read(book(later, 1), "$.tickets[0].code"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.result").value("TOO_EARLY"));
        mvc.perform(as(orgToken, get("/api/v1/organizer/events/" + later.getId() + "/check-ins/summary")))
            .andExpect(jsonPath("$.open").value(false));
    }

    @Test
    void simultaneousScansOfTheSameTicketAdmitOnlyOnce() throws Exception {
        Event e = soonEvent();
        String code = JsonPath.read(book(e, 1), "$.tickets[0].code");

        List<Integer> statuses = runConcurrently(List.of(
                () -> checkIn(orgToken, e, code).andReturn().getResponse().getStatus(),
                () -> checkIn(orgToken, e, code).andReturn().getResponse().getStatus(),
                () -> checkIn(orgToken, e, code).andReturn().getResponse().getStatus(),
                () -> checkIn(orgToken, e, code).andReturn().getResponse().getStatus()));

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from check_ins c join tickets t on t.id = c.ticket_id where t.event_id = ?",
                Integer.class, e.getId())).isEqualTo(1);
    }

    // ---------------------------------------------------------------- email

    @Test
    void confirmationEmailIsSentAfterBookingWithTheTicketCodes() throws Exception {
        Event e = soonEvent();
        String booking = book(e, 2);
        String reference = JsonPath.read(booking, "$.reference");

        MimeMessage message = mail.awaitMessage(m -> subject(m).contains(reference), Duration.ofSeconds(10))
                .orElseThrow(() -> new AssertionError("no confirmation email for " + reference));
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(attendee.getEmail());
        assertThat(subject(message)).contains(e.getTitle());
        String raw = toRaw(message);
        for (String code : (List<String>) JsonPath.read(booking, "$.tickets[*].code")) {
            assertThat(raw).contains(code);
        }
        assertThat(raw).contains("http://frontend.test/bookings/" + JsonPath.read(booking, "$.id"));
    }

    @Test
    void retriedBookingDoesNotSendASecondEmail() throws Exception {
        Event e = soonEvent();
        String ticketTypeId = e.getTicketTypes().getFirst().getId().toString();
        String body = "{\"eventId\":\"" + e.getId() + "\",\"items\":[{\"ticketTypeId\":\"" + ticketTypeId + "\",\"quantity\":1}]}";
        String key = UUID.randomUUID().toString();
        String reference = JsonPath.read(mvc.perform(as(attendeeToken, post("/api/v1/bookings")).header("Idempotency-Key", key).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.reference");
        mvc.perform(as(attendeeToken, post("/api/v1/bookings")).header("Idempotency-Key", key).content(body)).andExpect(status().isOk());

        mail.awaitMessage(m -> subject(m).contains(reference), Duration.ofSeconds(10)).orElseThrow();
        Thread.sleep(500); // give a (wrong) second email the chance to arrive
        assertThat(mail.sentTo(attendee.getEmail()).stream().filter(m -> subject(m).contains(reference))).hasSize(1);
    }

    // ---------------------------------------------------------------- helpers

    private static String subject(MimeMessage m) {
        try {
            return m.getSubject() == null ? "" : m.getSubject();
        } catch (Exception ex) {
            return "";
        }
    }

    private static String toRaw(MimeMessage m) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        m.writeTo(out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

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
