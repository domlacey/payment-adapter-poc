package com.example.paymentadapter.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Internal payment request. {@code amount} is in minor units (1250 GBP = £12.50).
 */
public record PaymentAuthorisationRequest(
        @NotBlank String paymentId,
        @NotNull @Positive Long amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 code") String currency) {
}
