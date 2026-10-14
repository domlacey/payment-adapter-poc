package com.example.paymentadapter.provider.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import com.example.paymentadapter.payment.PaymentAuthorisationRequest;
import com.example.paymentadapter.payment.PaymentAuthorisationResponse;
import com.example.paymentadapter.payment.PaymentStatus;
import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.payment.ProviderException.Reason;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/** Runs the shipped JSONPlaceholder flow file (and some deliberately bad ones) against a local fake server. */
class ConfigDrivenPaymentProviderTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PaymentAuthorisationRequest REQUEST = new PaymentAuthorisationRequest("PAY-1001", 1250L, "GBP");

    private static final String MERCHANT_POST = "{\"userId\":1,\"id\":1,\"title\":\"sunt\",\"body\":\"quia\"}";
    private static final String SENT_TITLE = "SIMULATED payment authorisation PAY-1001";
    private static final String SENT_BODY = "paymentId=PAY-1001;amountMinorUnits=1250;currency=GBP";
    private static final String CREATED_POST =
            "{\"title\":\"" + SENT_TITLE + "\",\"body\":\"" + SENT_BODY + "\",\"userId\":1,\"id\":101}";

    private FakeProviderServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeProviderServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }


    @Test
    void callsGetThenPostAndMapsToCanonicalResponse() {
        server.stub("GET", "/posts/1", 200, MERCHANT_POST).stub("POST", "/posts", 201, CREATED_POST);

        PaymentAuthorisationResponse response = provider("classpath:mappings/jsonplaceholder/").authorise(REQUEST);

        assertThat(response.paymentId()).isEqualTo("PAY-1001");
        assertThat(response.providerReference()).isEqualTo("JSONPLACEHOLDER-POST-101");
        assertThat(response.status()).isEqualTo(PaymentStatus.SIMULATED_ACCEPTED);
        assertThat(response.message()).startsWith("SIMULATION ONLY").contains("HTTP 201", "post id 101");
        assertThat(response.provider()).isEqualTo("jsonplaceholder");
        assertThat(response.simulation()).isTrue();
        assertThat(server.requests()).extracting(FakeProviderServer.Recorded::method, FakeProviderServer.Recorded::path)
                .containsExactly(tuple("GET", "/posts/1"), tuple("POST", "/posts"));
        assertThat(server.requests().get(1).body()).isEqualTo(
                "{\"userId\":1,\"title\":\"" + SENT_TITLE + "\",\"body\":\"" + SENT_BODY + "\"}");
    }

    @Test
    void merchantMismatchStopsBeforePost() {
        server.stub("GET", "/posts/1", 200, "{\"userId\":9,\"id\":1}");

        assertMalformed(provider("classpath:mappings/jsonplaceholder/"), "different userId");
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void lookupHttpErrorStopsBeforePost() {
        server.stub("GET", "/posts/1", 503, "{}");

        assertThatThrownBy(() -> provider("classpath:mappings/jsonplaceholder/").authorise(REQUEST))
                .isInstanceOfSatisfying(ProviderException.class, e -> assertThat(e.reason()).isEqualTo(Reason.HTTP_ERROR));
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void missingProviderIdIsMalformed() {
        server.stub("GET", "/posts/1", 200, MERCHANT_POST)
              .stub("POST", "/posts", 201, "{\"title\":\"" + SENT_TITLE + "\",\"body\":\"" + SENT_BODY + "\"}");

        assertMalformed(provider("classpath:mappings/jsonplaceholder/"), "missing 'id'");
    }

    @Test
    void responseThatDoesNotEchoRequestIsMalformed() {
        server.stub("GET", "/posts/1", 200, MERCHANT_POST)
              .stub("POST", "/posts", 201, "{\"title\":\"other\",\"body\":\"" + SENT_BODY + "\",\"id\":101}");

        assertMalformed(provider("classpath:mappings/jsonplaceholder/"), "echo the submitted title");
    }

    @Test
    void unexpectedJsonShapeIsMalformed() {
        server.stub("GET", "/posts/1", 200, "[1,2,3]");

        assertMalformed(provider("classpath:mappings/jsonplaceholder/"), "no id");
    }

    @Test
    void mappingCannotInventStatusOutsideCanonicalSchema() {
        server.stub("GET", "/thing", 200, "{\"ref\":\"R1\"}");

        assertMalformed(provider("classpath:test-mappings/authorised-status/"), "canonical schema");
    }

    @Test
    void mappingMustProduceRequiredCanonicalFields() {
        server.stub("GET", "/thing", 200, "{}");

        assertMalformed(provider("classpath:test-mappings/missing-reference/"), "providerReference");
    }

    @Test
    void platformOwnsProviderAndSimulationFlags() {
        server.stub("GET", "/thing", 200, "{\"ref\":\"R1\"}");

        PaymentAuthorisationResponse response = provider("classpath:test-mappings/simulation-false/").authorise(REQUEST);

        assertThat(response.simulation()).isTrue();
        assertThat(response.provider()).isEqualTo("jsonplaceholder");
    }

    private void assertMalformed(ConfigDrivenPaymentProvider provider, String messagePart) {
        assertThatThrownBy(() -> provider.authorise(REQUEST))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.MALFORMED_RESPONSE))
                .hasMessageContaining(messagePart);
    }

    private ConfigDrivenPaymentProvider provider(String mappingLocation) {
        ProvidersProperties.ProviderSettings settings = new ProvidersProperties.ProviderSettings(
                server.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1), true, mappingLocation,
                Map.of("merchantUserId", 1));
        FlowDefinitionLoader loader = new FlowDefinitionLoader(new DefaultResourceLoader(), MAPPER);
        return new ConfigDrivenPaymentProvider("jsonplaceholder", settings, loader, MAPPER, VALIDATOR);
    }
}
