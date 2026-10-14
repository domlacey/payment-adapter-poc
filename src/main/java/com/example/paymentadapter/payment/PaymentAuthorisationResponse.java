package com.example.paymentadapter.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Internal (canonical), provider-agnostic payment response. The constraints are enforced on every
 * response produced by a provider mapping.
 */
public record PaymentAuthorisationResponse(
        @NotBlank String paymentId,
        @NotBlank String providerReference,
        @NotNull PaymentStatus status,
        @NotBlank String message,
        String provider,
        boolean simulation) {
}
