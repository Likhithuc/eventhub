package com.eventhub.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.eventhub.support.IntegrationTest;
import com.eventhub.support.TestUsers;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.spec.SecretKeySpec;

/**
 * Role matrix enforced by the backend, using real tokens issued by /auth/login.
 *
 *                       anonymous  attendee  organizer  admin
 *  GET /users/me           401       200       200      200
 *  GET /organizer/...      401       403       200      200
 *  GET /admin/users        401       403       403      200
 */
@IntegrationTest
class AuthorizationIntegrationTest {

    private static final String ME = "/api/v1/users/me";
    private static final String ORGANIZER = "/api/v1/organizer/dashboard";
    private static final String ADMIN = "/api/v1/admin/users";

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired JwtEncoder jwtEncoder;

    private static MockHttpServletRequestBuilder as(String token, String path) {
        return get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    @Nested
    class Anonymous {
        @Test
        void protectedEndpointsReturn401() throws Exception {
            mvc.perform(get(ME)).andExpect(status().isUnauthorized());
            mvc.perform(get(ORGANIZER)).andExpect(status().isUnauthorized());
            mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        }

        @Test
        void publicEndpointsStayOpen() throws Exception {
            mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        }
    }

    @Nested
    class Attendee {
        String token;

        @BeforeEach
        void login() throws Exception {
            token = TestUsers.accessToken(mvc, testUsers.create("attendee", EnumSet.of(Role.ATTENDEE)));
        }

        @Test
        void canReadOwnProfile() throws Exception {
            mvc.perform(as(token, ME)).andExpect(status().isOk());
        }

        @Test
        void cannotUseOrganizerOrAdminEndpoints() throws Exception {
            mvc.perform(as(token, ORGANIZER)).andExpect(status().isForbidden());
            mvc.perform(as(token, ADMIN)).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Organizer {
        String token;

        @BeforeEach
        void login() throws Exception {
            token = TestUsers.accessToken(mvc, testUsers.create("organizer", EnumSet.of(Role.ORGANIZER, Role.ATTENDEE)));
        }

        @Test
        void canUseOrganizerArea() throws Exception {
            mvc.perform(as(token, ORGANIZER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.total").isNumber());
        }

        @Test
        void cannotUseAdminEndpoints() throws Exception {
            mvc.perform(as(token, ADMIN)).andExpect(status().isForbidden());
        }
    }

    @Nested
    class Admin {
        String token;

        @BeforeEach
        void login() throws Exception {
            token = TestUsers.accessToken(mvc, testUsers.create("admin", EnumSet.of(Role.ADMIN)));
        }

        @Test
        void canUseEverything() throws Exception {
            mvc.perform(as(token, ME)).andExpect(status().isOk());
            mvc.perform(as(token, ORGANIZER)).andExpect(status().isOk());
            mvc.perform(as(token, ADMIN + "?size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        }
    }

    @Nested
    class TokenTampering {
        User attendee;

        @BeforeEach
        void create() {
            attendee = testUsers.create("tamper", EnumSet.of(Role.ATTENDEE));
        }

        private String sign(JwtEncoder encoder, String issuer, Instant expiresAt, List<String> roles) {
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(attendee.getId().toString())
                    .issuedAt(expiresAt.minusSeconds(900))
                    .expiresAt(expiresAt)
                    .claim("email", attendee.getEmail())
                    .claim("roles", roles)
                    .build();
            return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
        }

        @Test
        void tokenSignedWithAnotherKeyIsRejectedEvenIfItClaimsAdmin() throws Exception {
            SecretKey attackerKey = new SecretKeySpec("attacker-controlled-key-32-bytes!!".getBytes(), "HmacSHA256");
            String forged = sign(new NimbusJwtEncoder(new ImmutableSecret<>(attackerKey)),
                    "eventhub-test", Instant.now().plusSeconds(600), List.of("ADMIN"));
            mvc.perform(as(forged, ADMIN)).andExpect(status().isUnauthorized());
        }

        @Test
        void expiredTokenIsRejected() throws Exception {
            String expired = sign(jwtEncoder, "eventhub-test", Instant.now().minusSeconds(120), List.of("ATTENDEE"));
            mvc.perform(as(expired, ME)).andExpect(status().isUnauthorized());
        }

        @Test
        void tokenFromAnotherIssuerIsRejected() throws Exception {
            String wrongIssuer = sign(jwtEncoder, "someone-else", Instant.now().plusSeconds(600), List.of("ATTENDEE"));
            mvc.perform(as(wrongIssuer, ME)).andExpect(status().isUnauthorized());
        }

        @Test
        void garbageTokenIsRejected() throws Exception {
            mvc.perform(as("not-a-jwt", ME)).andExpect(status().isUnauthorized());
        }
    }
}
