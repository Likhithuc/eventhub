package com.eventhub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async (used for sending emails off the request thread). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
