package com.eventhub.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled     send emails at all (off in tests unless a fake sender is installed)
 * @param from        From header, e.g. "EventHub &lt;no-reply@eventhub.local&gt;"
 * @param frontendUrl base URL of the React app, used for links in emails
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(boolean enabled, String from, String frontendUrl) {
}
