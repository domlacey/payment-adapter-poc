package com.example.paymentadapter.provider.jsonplaceholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;

import com.example.paymentadapter.payment.PaymentAuthorisationRequest;
import com.example.paymentadapter.payment.PaymentAuthorisationResponse;
import com.example.paymentadapter.payment.PaymentStatus;
import com.example.paymentadapter.payment.ProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonPlaceholderPaymentAdapterTest {

    private static final String MERCHANT_POST = "{\"userId\":1,\"id\":1,\"title\":\"sunt aut facere\",\"body\":\"quia\"}";
    private static final String CREATED_POST = "{\"title\":\"SIMULATED payment authorisation PAY-1001\","
            + "\"body\":\"paymentId=PAY-1001;amountMinorUnits=1250;currency=GBP\",\"userId\":1,\"id\":101}";

    private FakeProviderServer server;
    private JsonPlaceholderPaymentAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeProviderServer();
        JsonPlaceholderProperties props = new JsonPlaceholderProperties(
                server.baseUrl(), 1, Duration.ofSeconds(1), Duration.ofSeconds(1));
        adapter = new JsonPlaceholderPaymentAdapter(new JsonPlaceholderHttpClient(props, new ObjectMapper()), props);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void callsGetThenPostAndMapsResponse() {
        server.stub("GET", "/posts/1", 200, MERCHANT_POST)
              .stub("POST", "/posts", 201, CREATED_POST);

        PaymentAuthorisationResponse response = adapter.authorise(new PaymentAuthorisationRequest("PAY-1001", 1250L, "GBP"));

        assertThat(response.status()).isEqualTo(PaymentStatus.SIMULATED_ACCEPTED);
        assertThat(response.providerReference()).isEqualTo("JSONPLACEHOLDER-POST-101");
        assertThat(server.requests()).extracting(FakeProviderServer.Recorded::method, FakeProviderServer.Recorded::path)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("GET", "/posts/1"),
                                 org.assertj.core.groups.Tuple.tuple("POST", "/posts"));
        assertThat(server.requests().get(1).body()).isEqualTo(
                "{\"title\":\"SIMULATED payment authorisation PAY-1001\","
                + "\"body\":\"paymentId=PAY-1001;amountMinorUnits=1250;currency=GBP\",\"userId\":1}");
    }

    @Test
    void doesNotPostWhenMerchantLookupFails() {
        server.stub("GET", "/posts/1", 503, "{}");

        assertThatThrownBy(() -> adapter.authorise(new PaymentAuthorisationRequest("PAY-1001", 1250L, "GBP")))
                .isInstanceOf(ProviderException.class);
        assertThat(server.requests()).hasSize(1);
    }
}
