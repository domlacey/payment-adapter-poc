package com.example.paymentadapter.provider.jsonplaceholder;

import java.util.Objects;

import com.example.paymentadapter.payment.PaymentAuthorisationRequest;
import com.example.paymentadapter.payment.PaymentAuthorisationResponse;
import com.example.paymentadapter.payment.PaymentStatus;
import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.payment.ProviderException.Reason;
import com.example.paymentadapter.provider.jsonplaceholder.dto.CreatePostRequest;
import com.example.paymentadapter.provider.jsonplaceholder.dto.PostResponse;

/**
 * Pure, stateless translation between the internal payment model and JSONPlaceholder's "post" format.
 *
 * <pre>
 * internal {paymentId, amount, currency}  ->  provider {title, body, userId}
 * provider {id, title, ...} + HTTP status ->  internal {paymentId, providerReference, status, message}
 * </pre>
 */
public final class JsonPlaceholderMapper {

    public static final String PROVIDER_NAME = "jsonplaceholder";
    static final String REFERENCE_PREFIX = "JSONPLACEHOLDER-POST-";

    private JsonPlaceholderMapper() {
    }

    public static CreatePostRequest toProviderRequest(PaymentAuthorisationRequest request, int merchantUserId) {
        String title = "SIMULATED payment authorisation " + request.paymentId();
        String body = "paymentId=" + request.paymentId()
                + ";amountMinorUnits=" + request.amount()
                + ";currency=" + request.currency();
        return new CreatePostRequest(title, body, merchantUserId);
    }

    /** Validates the GET /posts/{merchantUserId} "merchant lookup" response. */
    public static void verifyMerchantLookup(PostResponse lookup, int merchantUserId) {
        if (lookup == null || lookup.id() == null || lookup.userId() == null) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    "Merchant lookup response is missing 'id' or 'userId'");
        }
        if (lookup.userId() != merchantUserId) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    "Merchant lookup returned userId " + lookup.userId() + ", expected " + merchantUserId);
        }
    }

    public static PaymentAuthorisationResponse toInternalResponse(PaymentAuthorisationRequest request,
                                                                  CreatePostRequest sent,
                                                                  int httpStatus,
                                                                  PostResponse created) {
        if (created == null || created.id() == null) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE, "Provider response is missing 'id'");
        }
        if (!Objects.equals(sent.title(), created.title()) || !Objects.equals(sent.body(), created.body())) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    "Provider response did not echo the submitted title/body");
        }
        String message = "SIMULATION ONLY - JSONPlaceholder returned HTTP " + httpStatus
                + " and assigned post id " + created.id() + ". No payment was authorised: JSONPlaceholder is a"
                + " fake REST API, not a payment processor, and it does not persist POST requests.";
        return new PaymentAuthorisationResponse(
                request.paymentId(),
                REFERENCE_PREFIX + created.id(),
                PaymentStatus.SIMULATED_ACCEPTED,
                message,
                PROVIDER_NAME,
                true);
    }
}
