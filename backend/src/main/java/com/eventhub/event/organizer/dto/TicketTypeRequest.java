package com.eventhub.event.organizer.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param salesStartAt optional; tickets are on sale immediately when null
 * @param salesEndAt   optional; sales close when the event starts when null
 */
public record TicketTypeRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @Min(1) @Max(100_000) Integer totalQuantity,
        Instant salesStartAt,
        Instant salesEndAt) {
}
