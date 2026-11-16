package com.eventhub.checkin;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.eventhub.booking.Booking;
import com.eventhub.booking.BookingItem;

/** Creates entry passes with unguessable credentials. */
@Component
public class TicketIssuer {

    /** No 0/O or 1/I, so codes are easy to read aloud and type. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TicketRepository tickets;

    public TicketIssuer(TicketRepository tickets) {
        this.tickets = tickets;
    }

    /** One pass per seat in the item. Attached to the booking; saved with it (cascade). */
    public void issue(Booking booking, BookingItem item) {
        for (int i = 0; i < item.getQuantity(); i++) {
            booking.addTicket(new Ticket(booking, item.getTicketType(), booking.getEvent(), newToken(), newCode()));
        }
    }

    /** 128 random bits, hex-encoded: the QR payload secret. */
    static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** 8 characters from a 32-letter alphabet (~10^12 combinations), checked for uniqueness. */
    private String newCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            String code = sb.toString();
            if (!tickets.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate a unique ticket code");
    }
}
