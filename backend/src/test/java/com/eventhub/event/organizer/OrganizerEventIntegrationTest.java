package com.eventhub.event.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.eventhub.event.EventRepository;
import com.eventhub.event.EventStatus;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.support.IntegrationTest;
import com.eventhub.support.TestEvents;
import com.eventhub.support.TestUsers;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.jayway.jsonpath.JsonPath;

/**
 * Organizer event management, driven through the real API with real tokens.
 * Phase 4 "done when": an organizer can manage their own event but cannot
 * edit another organizer's event.
 */
@IntegrationTest
class OrganizerEventIntegrationTest {

    private static final String EVENTS = "/api/v1/organizer/events";

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestEvents testEvents;
    @Autowired EventRepository eventRepository;

    String aliceToken;
    String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        aliceToken = tokenFor(testEvents.organizer("Alice Events"));
        bobToken = tokenFor(testEvents.organizer("Bob Promotions"));
    }

    private String tokenFor(OrganizerProfile profile) throws Exception {
        return TestUsers.accessToken(mvc, profile.getUser());
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private static String eventJson(String title, Instant start, Instant end) {
        return """
                {"title":"%s","description":"A great event for everyone.","category":"music",
                 "venueName":"The Hall","address":"1 Main Road","city":"Bengaluru",
                 "startsAt":"%s","endsAt":"%s"}""".formatted(title, start, end);
    }

    private static String eventJson(String title) {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        return eventJson(title, start, start.plus(3, ChronoUnit.HOURS));
    }

    private static String ticketJson(String name, String price, int qty) {
        return """
                {"name":"%s","price":%s,"totalQuantity":%d}""".formatted(name, price, qty);
    }

    private String createEvent(String token, String title) throws Exception {
        String body = mvc.perform(as(token, post(EVENTS)).content(eventJson(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions addTicket(String token, String eventId, String name, String price, int qty) throws Exception {
        return mvc.perform(as(token, post(EVENTS + "/" + eventId + "/ticket-types")).content(ticketJson(name, price, qty)));
    }

    private String ticketId(String token, String eventId, String name) throws Exception {
        String body = mvc.perform(as(token, get(EVENTS + "/" + eventId))).andReturn().getResponse().getContentAsString();
        java.util.List<String> ids = JsonPath.read(body, "$.ticketTypes[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    // ------------------------------------------------------------ organizer profile

    @Test
    void attendeeBecomesOrganizerByCreatingAProfile() throws Exception {
        User attendee = testUsers.create("newbie", EnumSet.of(Role.ATTENDEE));
        String token = TestUsers.accessToken(mvc, attendee);

        mvc.perform(as(token, get("/api/v1/me/organizer-profile"))).andExpect(status().isNotFound());
        mvc.perform(as(token, get(EVENTS))).andExpect(status().isForbidden());

        mvc.perform(as(token, post("/api/v1/me/organizer-profile"))
                .content("{\"displayName\":\"Newbie Nights\",\"description\":\"Small gigs\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.displayName").value("Newbie Nights"))
            .andExpect(jsonPath("$.contactEmail").value(attendee.getEmail()))
            .andExpect(jsonPath("$.verificationStatus").value("PENDING"));

        // A fresh token (as after /auth/refresh) now carries the ORGANIZER role.
        String newToken = TestUsers.accessToken(mvc, attendee);
        mvc.perform(as(newToken, get("/api/v1/users/me")))
            .andExpect(jsonPath("$.roles", contains("ATTENDEE", "ORGANIZER")));
        mvc.perform(as(newToken, get(EVENTS))).andExpect(status().isOk());

        mvc.perform(as(newToken, post("/api/v1/me/organizer-profile")).content("{\"displayName\":\"Again\"}"))
            .andExpect(status().isConflict());
        mvc.perform(as(newToken, put("/api/v1/me/organizer-profile"))
                .content("{\"displayName\":\"Renamed Nights\",\"contactEmail\":\"Hello@Example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Renamed Nights"))
            .andExpect(jsonPath("$.contactEmail").value("hello@example.com"));
    }

    @Test
    void profileValidationAndAuthentication() throws Exception {
        mvc.perform(post("/api/v1/me/organizer-profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\"}"))
            .andExpect(status().isUnauthorized());
        String token = TestUsers.accessToken(mvc, testUsers.create("val", EnumSet.of(Role.ATTENDEE)));
        mvc.perform(as(token, post("/api/v1/me/organizer-profile")).content("{\"displayName\":\"\",\"contactEmail\":\"nope\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.displayName").exists())
            .andExpect(jsonPath("$.errors.contactEmail").exists());
    }

    @Test
    void organizerRoleWithoutProfileMustCreateOneFirst() throws Exception {
        String token = TestUsers.accessToken(mvc, testUsers.create("noprofile", EnumSet.of(Role.ORGANIZER)));
        mvc.perform(as(token, post(EVENTS)).content(eventJson("Orphan")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Create your organizer profile first"));
        mvc.perform(as(token, get("/api/v1/organizer/dashboard")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile").doesNotExist())
            .andExpect(jsonPath("$.stats.total").value(0));
    }

    // ------------------------------------------------------------ create / read / update

    @Test
    void createsDraftThatIsNotPublic() throws Exception {
        String id = createEvent(aliceToken, "Alice Draft");

        mvc.perform(as(aliceToken, get(EVENTS + "/" + id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.editable").value(true))
            .andExpect(jsonPath("$.category.slug").value("music"))
            .andExpect(jsonPath("$.ticketTypes.length()").value(0));
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void validatesEventInput() throws Exception {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
        mvc.perform(as(aliceToken, post(EVENTS)).content(eventJson("Backwards", start, start.minusSeconds(60))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("The event must end after it starts"));
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        mvc.perform(as(aliceToken, post(EVENTS)).content(eventJson("Past", past, past.plus(1, ChronoUnit.HOURS))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("The event must start in the future"));
        mvc.perform(as(aliceToken, post(EVENTS)).content(eventJson("Too long", start, start.plus(31, ChronoUnit.DAYS))))
            .andExpect(status().isBadRequest());
        mvc.perform(as(aliceToken, post(EVENTS)).content(eventJson("Bad cat").replace("\"music\"", "\"opera\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Unknown category 'opera'"));
        mvc.perform(as(aliceToken, post(EVENTS)).content("{\"title\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.title").exists())
            .andExpect(jsonPath("$.errors.startsAt").exists());
    }

    @Test
    void updatesOwnDraft() throws Exception {
        String id = createEvent(aliceToken, "Old title");
        Instant start = Instant.now().plus(20, ChronoUnit.DAYS);
        mvc.perform(as(aliceToken, put(EVENTS + "/" + id))
                .content(eventJson("New title", start, start.plus(2, ChronoUnit.HOURS)).replace("\"music\"", "\"tech\"")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("New title"))
            .andExpect(jsonPath("$.category.slug").value("tech"));
    }

    @Test
    void listsOnlyOwnEventsWithStatusFilterAndTicketStats() throws Exception {
        String a1 = createEvent(aliceToken, "Alice One");
        createEvent(aliceToken, "Alice Two");
        createEvent(bobToken, "Bob One");
        addTicket(aliceToken, a1, "General", "100", 50).andExpect(status().isCreated());
        addTicket(aliceToken, a1, "VIP", "500", 10).andExpect(status().isCreated());
        mvc.perform(as(aliceToken, post(EVENTS + "/" + a1 + "/submit"))).andExpect(status().isOk());

        mvc.perform(as(aliceToken, get(EVENTS)))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[?(@.title == 'Bob One')]").isEmpty());
        mvc.perform(as(aliceToken, get(EVENTS)).param("status", "PENDING_APPROVAL"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Alice One"))
            .andExpect(jsonPath("$.content[0].ticketTypes").value(2))
            .andExpect(jsonPath("$.content[0].capacity").value(60));
        mvc.perform(as(aliceToken, get("/api/v1/organizer/dashboard")))
            .andExpect(jsonPath("$.profile.displayName").value("Alice Events"))
            .andExpect(jsonPath("$.stats.total").value(2))
            .andExpect(jsonPath("$.stats.draft").value(1))
            .andExpect(jsonPath("$.stats.pendingApproval").value(1));
    }

    // ------------------------------------------------------------ ownership (the "done when" criterion)

    @Test
    void organizerCannotReadOrChangeAnotherOrganizersEvent() throws Exception {
        String id = createEvent(aliceToken, "Alice Private");
        addTicket(aliceToken, id, "General", "100", 50).andExpect(status().isCreated());
        String ticket = ticketId(aliceToken, id, "General");
        String base = EVENTS + "/" + id;

        mvc.perform(as(bobToken, get(base))).andExpect(status().isNotFound());
        mvc.perform(as(bobToken, put(base)).content(eventJson("Hijacked"))).andExpect(status().isNotFound());
        mvc.perform(as(bobToken, post(base + "/submit"))).andExpect(status().isNotFound());
        mvc.perform(as(bobToken, post(base + "/cancel"))).andExpect(status().isNotFound());
        mvc.perform(as(bobToken, delete(base))).andExpect(status().isNotFound());
        addTicket(bobToken, id, "Fake", "1", 1).andExpect(status().isNotFound());
        mvc.perform(as(bobToken, put(base + "/ticket-types/" + ticket)).content(ticketJson("Free", "0", 9999)))
            .andExpect(status().isNotFound());
        mvc.perform(as(bobToken, delete(base + "/ticket-types/" + ticket))).andExpect(status().isNotFound());

        // Alice's event is untouched.
        mvc.perform(as(aliceToken, get(base)))
            .andExpect(jsonPath("$.title").value("Alice Private"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.ticketTypes[0].name").value("General"))
            .andExpect(jsonPath("$.ticketTypes[0].price").value(100.0));
    }

    @Test
    void attendeeCannotUseOrganizerEndpoints() throws Exception {
        String attendee = TestUsers.accessToken(mvc, testUsers.create("plain", EnumSet.of(Role.ATTENDEE)));
        String id = createEvent(aliceToken, "Alice Show");
        mvc.perform(as(attendee, get(EVENTS))).andExpect(status().isForbidden());
        mvc.perform(as(attendee, post(EVENTS)).content(eventJson("Nope"))).andExpect(status().isForbidden());
        mvc.perform(as(attendee, put(EVENTS + "/" + id)).content(eventJson("Nope"))).andExpect(status().isForbidden());
        mvc.perform(post(EVENTS).contentType(MediaType.APPLICATION_JSON).content(eventJson("Anon")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminHasSupportAccessToAnyEvent() throws Exception {
        String admin = TestUsers.accessToken(mvc, testUsers.create("admin", EnumSet.of(Role.ADMIN)));
        String id = createEvent(aliceToken, "Alice Supported");
        mvc.perform(as(admin, get(EVENTS + "/" + id)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Alice Supported"));
    }

    @Test
    void unknownEventIsNotFound() throws Exception {
        mvc.perform(as(aliceToken, get(EVENTS + "/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ ticket types

    @Test
    void managesTicketTypes() throws Exception {
        String id = createEvent(aliceToken, "Tickets");
        addTicket(aliceToken, id, "General", "499.50", 100)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ticketTypes[0].price").value(499.5))
            .andExpect(jsonPath("$.ticketTypes[0].currency").value("INR"));
        addTicket(aliceToken, id, "general", "10", 5)
            .andExpect(status().isConflict());
        addTicket(aliceToken, id, "Bad", "-1", 5).andExpect(status().isBadRequest());
        addTicket(aliceToken, id, "Zero", "10", 0).andExpect(status().isBadRequest());

        String ticket = ticketId(aliceToken, id, "General");
        mvc.perform(as(aliceToken, put(EVENTS + "/" + id + "/ticket-types/" + ticket)).content(ticketJson("Standard", "0", 80)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketTypes[0].name").value("Standard"))
            .andExpect(jsonPath("$.ticketTypes[0].totalQuantity").value(80));

        Instant afterEnd = Instant.now().plus(60, ChronoUnit.DAYS);
        mvc.perform(as(aliceToken, post(EVENTS + "/" + id + "/ticket-types"))
                .content("{\"name\":\"Late\",\"price\":1,\"totalQuantity\":1,\"salesEndAt\":\"" + afterEnd + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Ticket sales can't continue after the event ends"));

        mvc.perform(as(aliceToken, delete(EVENTS + "/" + id + "/ticket-types/" + ticket)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketTypes.length()").value(0));
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    void submitRequiresTicketsThenLocksEditingUntilWithdrawn() throws Exception {
        String id = createEvent(aliceToken, "Lifecycle");
        String base = EVENTS + "/" + id;

        mvc.perform(as(aliceToken, post(base + "/submit")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Add at least one ticket type before submitting"));

        addTicket(aliceToken, id, "General", "0", 100).andExpect(status().isCreated());
        mvc.perform(as(aliceToken, post(base + "/submit")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.editable").value(false));

        // Pending: still not public, and locked for edits.
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
        mvc.perform(as(aliceToken, put(base)).content(eventJson("Sneaky edit")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("This event is awaiting approval. Withdraw it to make changes."));
        addTicket(aliceToken, id, "VIP", "100", 1).andExpect(status().isConflict());
        mvc.perform(as(aliceToken, post(base + "/submit"))).andExpect(status().isConflict());
        mvc.perform(as(aliceToken, delete(base))).andExpect(status().isConflict());

        mvc.perform(as(aliceToken, post(base + "/withdraw")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(as(aliceToken, put(base)).content(eventJson("Edited after withdraw"))).andExpect(status().isOk());
    }

    @Test
    void rejectedEventCanBeEditedAndResubmitted() throws Exception {
        String id = createEvent(aliceToken, "Rejected once");
        addTicket(aliceToken, id, "General", "0", 10).andExpect(status().isCreated());
        var event = eventRepository.findById(UUID.fromString(id)).orElseThrow();
        event.setStatus(EventStatus.REJECTED);
        eventRepository.saveAndFlush(event);

        mvc.perform(as(aliceToken, put(EVENTS + "/" + id)).content(eventJson("Fixed"))).andExpect(status().isOk());
        mvc.perform(as(aliceToken, post(EVENTS + "/" + id + "/submit")))
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void publishedEventIsLockedButCanBeCancelledWhichHidesIt() throws Exception {
        String id = createEvent(aliceToken, "Going live");
        addTicket(aliceToken, id, "General", "0", 10).andExpect(status().isCreated());
        var event = eventRepository.findById(UUID.fromString(id)).orElseThrow();
        event.setStatus(EventStatus.PUBLISHED); // admin approval arrives in Phase 5
        eventRepository.saveAndFlush(event);
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk());

        mvc.perform(as(aliceToken, put(EVENTS + "/" + id)).content(eventJson("Changed")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Published events can't be edited."));
        mvc.perform(as(aliceToken, delete(EVENTS + "/" + id))).andExpect(status().isConflict());

        mvc.perform(as(aliceToken, post(EVENTS + "/" + id + "/cancel")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
        mvc.perform(as(aliceToken, post(EVENTS + "/" + id + "/cancel"))).andExpect(status().isConflict());
    }

    @Test
    void deletingADraftRemovesItAndItsTickets() throws Exception {
        String id = createEvent(aliceToken, "Throwaway");
        addTicket(aliceToken, id, "General", "0", 10).andExpect(status().isCreated());
        mvc.perform(as(aliceToken, delete(EVENTS + "/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(aliceToken, get(EVENTS + "/" + id))).andExpect(status().isNotFound());
        assertThat(eventRepository.findById(UUID.fromString(id))).isEmpty();
    }
}
