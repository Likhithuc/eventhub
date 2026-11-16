package com.eventhub.health;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight health check the React app calls to confirm the backend and
 * database are reachable. Infrastructure probes should use /actuator/health.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        boolean dbUp;
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbUp = one != null && one == 1;
        } catch (RuntimeException ex) {
            dbUp = false;
        }

        HealthResponse body = new HealthResponse(
                dbUp ? "UP" : "DEGRADED",
                "eventhub-backend",
                dbUp ? "UP" : "DOWN",
                Instant.now());
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
