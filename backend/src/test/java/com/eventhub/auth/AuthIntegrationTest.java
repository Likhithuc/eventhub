package com.eventhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eventhub.support.IntegrationTest;
import com.eventhub.support.TestUsers;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.eventhub.user.UserStatus;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired JdbcTemplate jdbc;

    private static String registerJson(String name, String email, String password) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private MvcResult register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Asha", email, TestUsers.PASSWORD)))
            .andReturn();
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshCookie.NAME);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie;
    }

    @Test
    void registerCreatesAttendeeWithHashedPasswordAndIssuesTokens() throws Exception {
        String email = TestUsers.uniqueEmail("New.User").toUpperCase();

        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Asha", email, TestUsers.PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
            .andExpect(jsonPath("$.user.roles[0]").value("ATTENDEE"))
            .andExpect(jsonPath("$.user.roles.length()").value(1))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(RefreshCookie.NAME + "=", "HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");

        String stored = jdbc.queryForObject("select password_hash from users where email = ?", String.class,
                email.toLowerCase());
        assertThat(stored).startsWith("{bcrypt}").doesNotContain(TestUsers.PASSWORD);

        Integer tokenRows = jdbc.queryForObject(
                "select count(*) from refresh_tokens t join users u on u.id = t.user_id where u.email = ?",
                Integer.class, email.toLowerCase());
        assertThat(tokenRows).isEqualTo(1);
        String rawRefresh = refreshCookie(result).getValue();
        Integer rawStored = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?",
                Integer.class, rawRefresh);
        assertThat(rawStored).as("raw refresh token must not be stored").isZero();
    }

    @Test
    void registerRejectsDuplicateEmailCaseInsensitively() throws Exception {
        String email = TestUsers.uniqueEmail("dup");
        assertThat(register(email).getResponse().getStatus()).isEqualTo(201);

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Other", email.toUpperCase(), TestUsers.PASSWORD)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("", "not-an-email", "short")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerCannotSelfAssignRoles() throws Exception {
        String email = TestUsers.uniqueEmail("sneaky");
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"email\":\"" + email + "\",\"password\":\"" + TestUsers.PASSWORD
                        + "\",\"roles\":[\"ADMIN\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.roles.length()").value(1))
            .andExpect(jsonPath("$.user.roles[0]").value("ATTENDEE"));
    }

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        User user = testUsers.create("login", EnumSet.of(Role.ATTENDEE));
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.json(user.getEmail().toUpperCase(), TestUsers.PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginFailsWithSameMessageForWrongPasswordAndUnknownEmail() throws Exception {
        User user = testUsers.create("wrongpw", EnumSet.of(Role.ATTENDEE));
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.json(user.getEmail(), "WrongPassword1")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.json("nobody@test.local", "WrongPassword1")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void suspendedUserCannotLogIn() throws Exception {
        User user = testUsers.create("suspended", EnumSet.of(Role.ATTENDEE));
        user.setStatus(UserStatus.SUSPENDED);
        testUsers.save(user);

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestUsers.json(user.getEmail(), TestUsers.PASSWORD)))
            .andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesTokenAndOldTokenStopsWorking() throws Exception {
        User user = testUsers.create("refresh", EnumSet.of(Role.ATTENDEE));
        Cookie original = refreshCookie(TestUsers.login(mvc, user.getEmail()));

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(original))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
            .andReturn();
        Cookie rotated = refreshCookie(refreshed);
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

        mvc.perform(post("/api/v1/auth/refresh").cookie(original))
            .andExpect(status().isUnauthorized());

        // Within the grace window the new token keeps working (benign race, e.g. two tabs).
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated))
            .andExpect(status().isOk());
    }

    @Test
    void reusingAnOldRotatedTokenRevokesAllSessions() throws Exception {
        User user = testUsers.create("reuse", EnumSet.of(Role.ATTENDEE));
        Cookie stolen = refreshCookie(TestUsers.login(mvc, user.getEmail()));
        Cookie current = refreshCookie(mvc.perform(post("/api/v1/auth/refresh").cookie(stolen)).andReturn());

        // Age the rotation beyond the grace window, then replay the stolen token.
        jdbc.update("update refresh_tokens set revoked_at = now() - interval '1 hour' where token_hash = ?",
                RefreshTokenService.hash(stolen.getValue()));
        mvc.perform(post("/api/v1/auth/refresh").cookie(stolen))
            .andExpect(status().isUnauthorized());

        // The legitimate session was revoked too.
        mvc.perform(post("/api/v1/auth/refresh").cookie(current))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        User user = testUsers.create("logout", EnumSet.of(Role.ATTENDEE));
        Cookie cookie = refreshCookie(TestUsers.login(mvc, user.getEmail()));

        MvcResult result = mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
            .andExpect(status().isNoContent())
            .andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        User user = testUsers.create("me", EnumSet.of(Role.ATTENDEE, Role.ORGANIZER));
        String token = TestUsers.accessToken(mvc, user);

        String body = mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.email")).isEqualTo(user.getEmail());
        assertThat((java.util.List<String>) JsonPath.read(body, "$.roles")).containsExactly("ATTENDEE", "ORGANIZER");
    }
}
