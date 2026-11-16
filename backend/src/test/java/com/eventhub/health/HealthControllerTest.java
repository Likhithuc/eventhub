package com.eventhub.health;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.eventhub.config.SecurityConfig;
import com.eventhub.security.AuthProperties;
import com.eventhub.security.JwtConfig;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@ActiveProfiles("test")
@EnableConfigurationProperties(AuthProperties.class)
class HealthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JdbcTemplate jdbcTemplate;

    @Test
    void returnsUpWhenDatabaseReachable() throws Exception {
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).willReturn(1);

        mvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void returns503WhenDatabaseDown() throws Exception {
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
            .willThrow(new CannotGetJdbcConnectionException("down"));

        mvc.perform(get("/api/v1/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.database").value("DOWN"));
    }

    @Test
    void allowsCorsFromViteDevServer() throws Exception {
        mvc.perform(options("/api/v1/health")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void otherEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/bookings/me"))
            .andExpect(status().isUnauthorized());
    }
}
