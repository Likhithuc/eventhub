package com.eventhub.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One real PostgreSQL per test JVM, shared by every test that imports this (Spring caches the context). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    /** Replaces SMTP in tests: emails are captured in memory so tests can inspect them. */
    @Bean
    RecordingMailSender recordingMailSender() {
        return new RecordingMailSender();
    }
}
