package com.eventhub.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.eventhub.user.UserRepository;
import com.jayway.jsonpath.JsonPath;

/** Creates users directly in the DB and logs them in through the real API. */
@Component
public class TestUsers {

    public static final String PASSWORD = "Password123!";

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public TestUsers(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.local";
    }

    public User create(String prefix, Set<Role> roles) {
        return users.saveAndFlush(new User(prefix, uniqueEmail(prefix), encoder.encode(PASSWORD), roles));
    }

    public User save(User user) {
        return users.saveAndFlush(user);
    }

    public static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    public static MvcResult login(MockMvc mvc, String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
    }

    public static String accessToken(MockMvc mvc, User user) throws Exception {
        return JsonPath.read(login(mvc, user.getEmail()).getResponse().getContentAsString(), "$.accessToken");
    }
}
