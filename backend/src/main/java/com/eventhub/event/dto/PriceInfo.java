package com.eventhub.event.dto;

import java.math.BigDecimal;

/**
 * Price range across an event's active ticket types.
 *
 * @param min      cheapest ticket, or null when the event has no ticket types yet
 * @param max      most expensive ticket, or null when the event has no ticket types yet
 * @param currency ISO 4217 code, e.g. INR
 * @param free     true when every ticket is free
 */
public record PriceInfo(BigDecimal min, BigDecimal max, String currency, boolean free) {

    public static PriceInfo of(BigDecimal min, BigDecimal max, String currency) {
        if (min == null || max == null) {
            return new PriceInfo(null, null, currency, false);
        }
        return new PriceInfo(min, max, currency, max.signum() == 0);
    }
}
