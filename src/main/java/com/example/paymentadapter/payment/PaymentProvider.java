package com.example.paymentadapter.payment;

/**
 * Port implemented by every external provider adapter. Adapters translate the internal model to the
 * provider's wire format and back, and signal failures by throwing {@link ProviderException}.
 */
public interface PaymentProvider {

    /** Key used in {@code payments.provider} to select this adapter. */
    String name();

    PaymentAuthorisationResponse authorise(PaymentAuthorisationRequest request);
}
