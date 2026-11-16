package com.eventhub.notification;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import com.eventhub.booking.Booking;
import com.eventhub.booking.BookingConfirmedEvent;
import com.eventhub.booking.BookingRepository;
import com.eventhub.checkin.Ticket;
import com.eventhub.event.Event;

import jakarta.mail.internet.MimeMessage;

/**
 * Sends the booking confirmation email.
 *
 * <p>Runs only after the booking transaction has committed, on a background thread, so a
 * slow or failing mail server can never delay or roll back a booking. Failures are logged;
 * the booking stays valid and the attendee can always see it under "My tickets".
 * (A durable outbox with retries would be the next step for production.)
 */
@Component
public class BookingMailer {

    private static final Logger log = LoggerFactory.getLogger(BookingMailer.class);

    private final JavaMailSender mailSender;
    private final BookingRepository bookings;
    private final MailProperties props;
    private final DateTimeFormatter when;

    public BookingMailer(JavaMailSender mailSender, BookingRepository bookings, MailProperties props,
            @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.mailSender = mailSender;
        this.bookings = bookings;
        this.props = props;
        this.when = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' h:mm a", Locale.ENGLISH)
                .withZone(ZoneId.of(timezone));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBookingConfirmed(BookingConfirmedEvent evt) {
        if (!props.enabled()) {
            return;
        }
        try {
            Booking booking = bookings.findWithDetailsById(evt.bookingId()).orElse(null);
            if (booking == null) return;
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.from());
            helper.setTo(booking.getUser().getEmail());
            helper.setSubject("Your tickets for " + booking.getEvent().getTitle() + " · " + booking.getReference());
            helper.setText(plainText(booking), html(booking));
            mailSender.send(message);
            log.info("Sent booking confirmation {} for booking {}", booking.getReference(), booking.getId());
        } catch (Exception ex) {
            // Never propagate: the booking is already committed and valid.
            log.error("Could not send confirmation email for booking {}", evt.bookingId(), ex);
        }
    }

    private String link(Booking b) {
        return props.frontendUrl().replaceAll("/$", "") + "/bookings/" + b.getId();
    }

    private String plainText(Booking b) {
        Event e = b.getEvent();
        StringBuilder sb = new StringBuilder()
                .append("Hi ").append(b.getUser().getName()).append(",\n\n")
                .append("You're going to ").append(e.getTitle()).append("!\n\n")
                .append("When:  ").append(when.format(e.getStartsAt())).append('\n')
                .append("Where: ").append(e.getVenueName()).append(", ").append(e.getAddress()).append(", ").append(e.getCity()).append("\n\n")
                .append("Booking reference: ").append(b.getReference()).append("\n\nYour tickets:\n");
        for (Ticket t : b.getTickets()) {
            sb.append("  - ").append(t.getTicketType().getName()).append(": ").append(t.displayCode()).append('\n');
        }
        return sb.append("\nShow the QR code for each ticket at the door: ").append(link(b))
                .append("\n\nSee you there,\nEventHub\n").toString();
    }

    private String html(Booking b) {
        Event e = b.getEvent();
        StringBuilder rows = new StringBuilder();
        for (Ticket t : b.getTickets()) {
            rows.append("<tr><td style=\"padding:8px 0;border-bottom:1px solid #eee\">").append(esc(t.getTicketType().getName()))
                .append("</td><td style=\"padding:8px 0;border-bottom:1px solid #eee;text-align:right;font-family:monospace;font-size:16px;letter-spacing:1px\">")
                .append(esc(t.displayCode())).append("</td></tr>");
        }
        return """
                <!doctype html><html><body style="margin:0;background:#f6f5fb;font-family:Segoe UI,Roboto,Arial,sans-serif;color:#17142b">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 12px">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;background:#fff;border-radius:16px;overflow:hidden">
                  <tr><td style="background:linear-gradient(135deg,#7c3aed,#ec4899);background-color:#7c3aed;padding:28px 28px 24px;color:#fff">
                    <div style="font-size:13px;letter-spacing:1px;text-transform:uppercase;opacity:.85">Booking confirmed</div>
                    <div style="font-size:24px;font-weight:700;margin-top:8px">%s</div>
                    <div style="margin-top:10px;font-size:14px;opacity:.95">%s</div>
                    <div style="font-size:14px;opacity:.95">%s, %s, %s</div>
                  </td></tr>
                  <tr><td style="padding:24px 28px">
                    <p style="margin:0 0 16px">Hi %s, you're going! Here are your tickets.</p>
                    <div style="font-size:12px;color:#6f6b86">Booking reference</div>
                    <div style="font-family:monospace;font-size:22px;letter-spacing:2px;margin:2px 0 18px">%s</div>
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="font-size:14px">%s</table>
                    <p style="margin:22px 0 0"><a href="%s" style="display:inline-block;background:#6d28d9;color:#fff;text-decoration:none;padding:12px 20px;border-radius:10px;font-weight:600">View tickets &amp; QR codes</a></p>
                    <p style="margin:18px 0 0;font-size:12px;color:#6f6b86">Each person shows their own QR code (or ticket code) at the entrance.</p>
                  </td></tr>
                </table>
                <p style="font-size:12px;color:#6f6b86;margin-top:14px">EventHub</p>
                </td></tr></table></body></html>
                """.formatted(esc(e.getTitle()), esc(when.format(e.getStartsAt())), esc(e.getVenueName()), esc(e.getAddress()),
                esc(e.getCity()), esc(b.getUser().getName()), esc(b.getReference()), rows, esc(link(b)));
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
