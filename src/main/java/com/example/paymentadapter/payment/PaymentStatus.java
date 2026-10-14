package com.example.paymentadapter.payment;

/**
 * Internal statuses. There is deliberately no AUTHORISED value: this POC can never authorise money.
 */
public enum PaymentStatus {
    /** The provider accepted the (simulated) request. No payment was authorised. */
    SIMULATED_ACCEPTED,
    /** The provider answered with a non-2xx HTTP status. */
    PROVIDER_ERROR,
    /** The provider did not answer within the configured timeout. */
    PROVIDER_TIMEOUT,
    /** The provider could not be reached (DNS, connection refused, TLS...). */
    PROVIDER_UNAVAILABLE,
    /** The provider answered 2xx but the body was not what the adapter expects. */
    INVALID_PROVIDER_RESPONSE
}
