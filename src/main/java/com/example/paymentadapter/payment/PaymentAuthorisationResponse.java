package com.example.paymentadapter.payment;

/**
 * Internal, provider-agnostic payment response returned to callers of the payments API.
 */
public record PaymentAuthorisationResponse(
        String paymentId,
        String providerReference,
        PaymentStatus status,
        String message,
        String provider,
        boolean simulation) {
}
