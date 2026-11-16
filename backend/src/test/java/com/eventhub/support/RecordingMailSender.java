package com.eventhub.support;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** In-memory JavaMailSender for tests: records every message instead of sending it. */
public class RecordingMailSender implements JavaMailSender {

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        try {
            return new MimeMessage(Session.getInstance(new Properties()), contentStream);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void send(MimeMessage... mimeMessages) {
        sent.addAll(List.of(mimeMessages));
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) {
        throw new UnsupportedOperationException("Only MIME messages are used");
    }

    public List<MimeMessage> sentTo(String email) {
        List<MimeMessage> result = new ArrayList<>();
        for (MimeMessage m : sent) {
            try {
                for (Address a : m.getRecipients(Message.RecipientType.TO)) {
                    if (a.toString().equalsIgnoreCase(email)) result.add(m);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return result;
    }

    /** Emails are sent asynchronously after commit, so wait briefly for one to arrive. */
    public Optional<MimeMessage> awaitMessage(Predicate<MimeMessage> match, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            for (MimeMessage m : sent) {
                if (match.test(m)) return Optional.of(m);
            }
            Thread.sleep(50);
        }
        return Optional.empty();
    }
}
