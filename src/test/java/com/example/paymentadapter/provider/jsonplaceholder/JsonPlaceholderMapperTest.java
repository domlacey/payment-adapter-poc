package com.example.paymentadapter.provider.jsonplaceholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.paymentadapter.payment.PaymentAuthorisationRequest;
import com.example.paymentadapter.payment.PaymentAuthorisationResponse;
import com.example.paymentadapter.payment.PaymentStatus;
import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.provider.jsonplaceholder.dto.CreatePostRequest;
import com.example.paymentadapter.provider.jsonplaceholder.dto.PostResponse;
import org.junit.jupiter.api.Test;

class JsonPlaceholderMapperTest {

    private static final PaymentAuthorisationRequest REQUEST = new PaymentAuthorisationRequest("PAY-1001", 1250L, "GBP");

    @Test
    void mapsInternalRequestToProviderPost() {
        CreatePostRequest post = JsonPlaceholderMapper.toProviderRequest(REQUEST, 1);

        assertThat(post.title()).isEqualTo("SIMULATED payment authorisation PAY-1001");
        assertThat(post.body()).isEqualTo("paymentId=PAY-1001;amountMinorUnits=1250;currency=GBP");
        assertThat(post.userId()).isEqualTo(1);
    }

    @Test
    void mapsProviderResponseToSimulatedInternalResponse() {
        CreatePostRequest sent = JsonPlaceholderMapper.toProviderRequest(REQUEST, 1);
        PostResponse created = new PostResponse(101, 1, sent.title(), sent.body());

        PaymentAuthorisationResponse response = JsonPlaceholderMapper.toInternalResponse(REQUEST, sent, 201, created);

        assertThat(response.paymentId()).isEqualTo("PAY-1001");
        assertThat(response.providerReference()).isEqualTo("JSONPLACEHOLDER-POST-101");
        assertThat(response.status()).isEqualTo(PaymentStatus.SIMULATED_ACCEPTED);
        assertThat(response.message()).startsWith("SIMULATION ONLY").contains("HTTP 201", "post id 101",
                "No payment was authorised");
        assertThat(response.provider()).isEqualTo("jsonplaceholder");
        assertThat(response.simulation()).isTrue();
    }

    @Test
    void rejectsResponseWithoutId() {
        CreatePostRequest sent = JsonPlaceholderMapper.toProviderRequest(REQUEST, 1);
        PostResponse created = new PostResponse(null, 1, sent.title(), sent.body());

        assertThatThrownBy(() -> JsonPlaceholderMapper.toInternalResponse(REQUEST, sent, 201, created))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(ProviderException.Reason.MALFORMED_RESPONSE))
                .hasMessageContaining("missing 'id'");
    }

    @Test
    void rejectsNullResponse() {
        CreatePostRequest sent = JsonPlaceholderMapper.toProviderRequest(REQUEST, 1);

        assertThatThrownBy(() -> JsonPlaceholderMapper.toInternalResponse(REQUEST, sent, 201, null))
                .isInstanceOf(ProviderException.class);
    }

    @Test
    void rejectsResponseThatDoesNotEchoRequest() {
        CreatePostRequest sent = JsonPlaceholderMapper.toProviderRequest(REQUEST, 1);
        PostResponse created = new PostResponse(101, 1, "something else", sent.body());

        assertThatThrownBy(() -> JsonPlaceholderMapper.toInternalResponse(REQUEST, sent, 201, created))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(ProviderException.Reason.MALFORMED_RESPONSE))
                .hasMessageContaining("echo");
    }

    @Test
    void merchantLookupRequiresMatchingUser() {
        JsonPlaceholderMapper.verifyMerchantLookup(new PostResponse(1, 1, "t", "b"), 1);

        assertThatThrownBy(() -> JsonPlaceholderMapper.verifyMerchantLookup(new PostResponse(1, 7, "t", "b"), 1))
                .isInstanceOf(ProviderException.class).hasMessageContaining("expected 1");
        assertThatThrownBy(() -> JsonPlaceholderMapper.verifyMerchantLookup(new PostResponse(null, null, null, null), 1))
                .isInstanceOf(ProviderException.class).hasMessageContaining("missing");
    }
}
