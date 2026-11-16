package com.eventhub.devseed;

import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eventhub.user.EmailNormalizer;
import com.eventhub.user.Role;
import com.eventhub.user.User;
import com.eventhub.user.UserRepository;

/**
 * Creates local development accounts. Safety rails:
 * <ul>
 *   <li>Runs only when {@code app.seed.enabled=true} (default false, so never in production by accident).</li>
 *   <li>Credentials come from environment variables, never from source code.</li>
 *   <li>An account is skipped if its password is blank or the email already exists; existing users are never modified.</li>
 *   <li>Passwords are never logged.</li>
 * </ul>
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DevAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

    @ConfigurationProperties(prefix = "app.seed")
    public record SeedProperties(boolean enabled, boolean demoEvents, Account admin, Account organizer) {
        public record Account(String email, String password) {
        }
    }

    private final SeedProperties props;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DevAccountSeeder(SeedProperties props, UserRepository users, PasswordEncoder passwordEncoder) {
        this.props = props;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.warn("Development account seeding is ENABLED (app.seed.enabled=true). Never enable this in production.");
        seed("Dev Admin", props.admin(), EnumSet.of(Role.ADMIN, Role.ATTENDEE));
        seed("Dev Organizer", props.organizer(), EnumSet.of(Role.ORGANIZER, Role.ATTENDEE));
    }

    private void seed(String name, SeedProperties.Account account, Set<Role> roles) {
        if (account == null || isBlank(account.email()) || isBlank(account.password())) {
            log.info("Skipping {} seed: email/password not configured", name);
            return;
        }
        if (account.password().length() < 8) {
            log.warn("Skipping {} seed: password must be at least 8 characters", name);
            return;
        }
        String email = EmailNormalizer.normalize(account.email());
        if (users.existsByEmail(email)) {
            log.info("{} account {} already exists; leaving it unchanged", name, email);
            return;
        }
        users.save(new User(name, email, passwordEncoder.encode(account.password()), roles));
        log.info("Seeded {} account {} with roles {}", name, email, roles);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
