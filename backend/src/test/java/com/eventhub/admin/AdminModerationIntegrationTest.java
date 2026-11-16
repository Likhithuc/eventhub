package com.eventhub.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.eventhub.event.Event;
import com.eventhub.event.EventRepository;
import com.eventhub.event.EventStatus;
import com.eventhub.organizer.OrganizerProfile;
import com.eventhub.support.IntegrationTest;
import com.eventhub.support.TestEvents;
import com.eventhub.support.TestUsers;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Phase 5 "done when": approved events become visible publicly and rejected
 * events do not; only admins can approve or reject.
 */
@IntegrationTest
class AdminModerationIntegrationTest {

    private static final String ADMIN_EVENTS = "/api/v1/admin/events";

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestEvents testEvents;
    @Autowired EventRepository eventRepository;

    User admin;
    String adminToken;
    OrganizerProfile org;
    String orgToken;
    String city;

    @BeforeEach
    void setUp() throws Exception {
        admin = testUsers.create("admin", EnumSet.of(Role.ADMIN));
        adminToken = TestUsers.accessToken(mvc, admin);
        org = testEvents.organizer("Moderated Promoters");
        orgToken = TestUsers.accessToken(mvc, org.getUser());
        city = TestEvents.uniqueCity();
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** A pending event created the way an organizer would: draft + ticket + submit through the API. */
    private String pendingEvent(String title) throws Exception {
        Event draft = testEvents.event(org, title).city(city).status(EventStatus.DRAFT).ticket("General", "0", 50).save();
        mvc.perform(as(orgToken, post("/api/v1/organizer/events/" + draft.getId() + "/submit")))
            .andExpect(status().isOk());
        return draft.getId().toString();
    }

    private static String reason(String text) {
        return "{\"reason\":\"" + text + "\"}";
    }

    @Test
    void approvingPublishesTheEventAndRecordsWhoApproved() throws Exception {
        String id = pendingEvent("To approve " + UUID.randomUUID());
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());

        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/approve")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.event.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.reviewedByName").value(admin.getName()))
            .andExpect(jsonPath("$.reviewedAt").isNotEmpty())
            .andExpect(jsonPath("$.history[0].action").value("EVENT_APPROVED"))
            .andExpect(jsonPath("$.history[0].actorName").value(admin.getName()));

        // Now public: details and search.
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/events").param("city", city))
            .andExpect(jsonPath("$.content[*].id", hasItem(id)));
    }

    @Test
    void rejectingKeepsTheEventHiddenAndShowsTheReasonToTheOrganizer() throws Exception {
        String id = pendingEvent("To reject");

        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/reject")).content(reason("Please add the full venue address")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.event.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectionReason").value("Please add the full venue address"));

        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events").param("city", city))
            .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));

        // Organizer sees why, fixes it and resubmits; the old reason is cleared.
        mvc.perform(as(orgToken, get("/api/v1/organizer/events/" + id)))
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.editable").value(true))
            .andExpect(jsonPath("$.rejectionReason").value("Please add the full venue address"));
        mvc.perform(as(orgToken, post("/api/v1/organizer/events/" + id + "/submit")))
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void rejectionRequiresAReason() throws Exception {
        String id = pendingEvent("Needs reason");
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/reject")).content(reason("")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.reason").exists());
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/reject")).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsCanModerate() throws Exception {
        String id = pendingEvent("Admins only");
        String attendee = TestUsers.accessToken(mvc, testUsers.create("att", EnumSet.of(Role.ATTENDEE)));

        for (String token : new String[] {orgToken, attendee}) {
            mvc.perform(as(token, get(ADMIN_EVENTS))).andExpect(status().isForbidden());
            mvc.perform(as(token, get(ADMIN_EVENTS + "/" + id))).andExpect(status().isForbidden());
            mvc.perform(as(token, post(ADMIN_EVENTS + "/" + id + "/approve"))).andExpect(status().isForbidden());
            mvc.perform(as(token, post(ADMIN_EVENTS + "/" + id + "/reject")).content(reason("nope nope")))
                .andExpect(status().isForbidden());
            mvc.perform(as(token, get("/api/v1/admin/overview"))).andExpect(status().isForbidden());
            mvc.perform(as(token, get("/api/v1/admin/organizers"))).andExpect(status().isForbidden());
        }
        mvc.perform(post(ADMIN_EVENTS + "/" + id + "/approve")).andExpect(status().isUnauthorized());

        // Still pending and hidden.
        mvc.perform(as(adminToken, get(ADMIN_EVENTS + "/" + id))).andExpect(jsonPath("$.event.status").value("PENDING_APPROVAL"));
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void onlyPendingFutureEventsCanBeApprovedOrRejected() throws Exception {
        Event draft = testEvents.event(org, "Just a draft").city(city).status(EventStatus.DRAFT).save();
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + draft.getId() + "/approve")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Only events pending approval can be approved (this one is draft)"));
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + draft.getId() + "/reject")).content(reason("Not pending")))
            .andExpect(status().isConflict());

        String id = pendingEvent("Approved twice");
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/approve"))).andExpect(status().isOk());
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/approve"))).andExpect(status().isConflict());

        // Pending, but its start time has passed while waiting in the queue.
        Event stale = testEvents.event(org, "Stale").city(city).status(EventStatus.PENDING_APPROVAL)
                .startsAt(Instant.now().minus(1, ChronoUnit.HOURS)).endsAt(Instant.now().plus(1, ChronoUnit.HOURS)).save();
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + stale.getId() + "/approve")))
            .andExpect(status().isConflict());

        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + UUID.randomUUID() + "/approve")))
            .andExpect(status().isNotFound());
    }

    @Test
    void unpublishingTakesALiveEventDownWithAReason() throws Exception {
        String id = pendingEvent("Live then down");
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/approve"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk());

        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/unpublish")).content(reason("Reported as misleading")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.event.status").value("REJECTED"))
            .andExpect(jsonPath("$.history[0].action").value("EVENT_UNPUBLISHED"));
        mvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());
        mvc.perform(as(orgToken, get("/api/v1/organizer/events/" + id)))
            .andExpect(jsonPath("$.rejectionReason").value("Reported as misleading"));
    }

    @Test
    void queueListsPendingEventsOldestSubmissionFirst() throws Exception {
        String tag = "Queue-" + UUID.randomUUID().toString().substring(0, 6);
        String first = pendingEvent(tag + " first");
        String second = pendingEvent(tag + " second");
        testEvents.event(org, tag + " draft").city(city).status(EventStatus.DRAFT).save();

        mvc.perform(as(adminToken, get(ADMIN_EVENTS)).param("status", "PENDING_APPROVAL").param("q", tag))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value(first))
            .andExpect(jsonPath("$.content[1].id").value(second))
            .andExpect(jsonPath("$.content[0].organizerName").value("Moderated Promoters"))
            .andExpect(jsonPath("$.content[0].submittedAt").isNotEmpty());
        mvc.perform(as(adminToken, get(ADMIN_EVENTS)).param("q", tag))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void suspendingAUserBlocksLoginAndRevokesSessionsUntilReactivated() throws Exception {
        User user = testUsers.create("suspendme", EnumSet.of(Role.ATTENDEE));
        Cookie refresh = TestUsers.login(mvc, user.getEmail()).getResponse().getCookie("eventhub_refresh");

        mvc.perform(as(adminToken, patch("/api/v1/admin/users/" + user.getId() + "/status")).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.json(user.getEmail(), TestUsers.PASSWORD)))
            .andExpect(status().isForbidden());

        mvc.perform(as(adminToken, patch("/api/v1/admin/users/" + user.getId() + "/status")).content("{\"status\":\"ACTIVE\"}"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
        TestUsers.login(mvc, user.getEmail());

        mvc.perform(as(adminToken, get("/api/v1/admin/audit-log")).param("size", "50"))
            .andExpect(jsonPath("$.content[*].action", hasItem("USER_SUSPENDED")))
            .andExpect(jsonPath("$.content[*].action", hasItem("USER_REACTIVATED")));
    }

    @Test
    void adminCannotSuspendThemselvesAndUserSearchFilters() throws Exception {
        mvc.perform(as(adminToken, patch("/api/v1/admin/users/" + admin.getId() + "/status")).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isBadRequest());

        mvc.perform(as(adminToken, get("/api/v1/admin/users")).param("q", org.getUser().getEmail().toUpperCase()))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(org.getUser().getId().toString()));
        mvc.perform(as(adminToken, get("/api/v1/admin/users")).param("q", org.getUser().getEmail()).param("role", "ADMIN"))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void verifyingAnOrganizerShowsTheBadgePublicly() throws Exception {
        String id = pendingEvent("Verified host");
        mvc.perform(as(adminToken, post(ADMIN_EVENTS + "/" + id + "/approve"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/events/" + id)).andExpect(jsonPath("$.organizer.verified").value(false));

        mvc.perform(as(adminToken, patch("/api/v1/admin/organizers/" + org.getId() + "/verification"))
                .content("{\"status\":\"VERIFIED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.publishedEvents").value(1));
        mvc.perform(get("/api/v1/events/" + id)).andExpect(jsonPath("$.organizer.verified").value(true));

        mvc.perform(as(adminToken, get("/api/v1/admin/organizers")).param("verification", "VERIFIED").param("size", "100"))
            .andExpect(jsonPath("$.content[*].id", hasItem(org.getId().toString())));
    }

    @Test
    void overviewCountsPendingEvents() throws Exception {
        String before = mvc.perform(as(adminToken, get("/api/v1/admin/overview"))).andReturn().getResponse().getContentAsString();
        int pendingBefore = JsonPath.read(before, "$.pendingApproval");
        pendingEvent("Counted");
        mvc.perform(as(adminToken, get("/api/v1/admin/overview")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingApproval").value(pendingBefore + 1))
            .andExpect(jsonPath("$.eventsByStatus.PENDING_APPROVAL").value(pendingBefore + 1))
            .andExpect(jsonPath("$.users.total").isNumber())
            .andExpect(jsonPath("$.organizers.total").isNumber());
    }
}
