package com.example.paymentadapter.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.example.paymentadapter.payment.ProviderException.Reason;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    private static final PaymentAuthorisationRequest REQUEST = new PaymentAuthorisationRequest("PAY-1001", 1250L, "GBP");

    @Test
    void returnsProviderResponseUnchangedOnSuccess() {
        PaymentAuthorisationResponse ok = new PaymentAuthorisationResponse(
                "PAY-1001", "REF-1", PaymentStatus.SIMULATED_ACCEPTED, "SIMULATION ONLY", "stub", true);
        PaymentService service = serviceWith(stub(r -> ok));

        assertThat(service.authorise(REQUEST)).isEqualTo(ok);
    }

    @ParameterizedTest
    @CsvSource({
            "HTTP_ERROR, PROVIDER_ERROR",
            "TIMEOUT, PROVIDER_TIMEOUT",
            "UNAVAILABLE, PROVIDER_UNAVAILABLE",
            "MALFORMED_RESPONSE, INVALID_PROVIDER_RESPONSE"
    })
    void mapsProviderFailuresToInternalStatus(Reason reason, PaymentStatus expected) {
        PaymentService service = serviceWith(stub(r -> {
            throw new ProviderException(reason, "provider said no");
        }));

        PaymentAuthorisationResponse response = service.authorise(REQUEST);

        assertThat(response.paymentId()).isEqualTo("PAY-1001");
        assertThat(response.providerReference()).isNull();
        assertThat(response.status()).isEqualTo(expected);
        assertThat(response.message()).startsWith("SIMULATION FAILED").contains("provider said no",
                "No payment was authorised");
        assertThat(response.simulation()).isTrue();
    }

    @Test
    void failsFastOnUnknownProvider() {
        assertThatThrownBy(() -> new PaymentService(List.of(stub(r -> null)), "wechatpay", new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("wechatpay");
    }

    private static PaymentService serviceWith(PaymentProvider provider) {
        return new PaymentService(List.of(provider), "stub", new ObjectMapper());
    }

    private static PaymentProvider stub(java.util.function.Function<PaymentAuthorisationRequest, PaymentAuthorisationResponse> fn) {
        return new PaymentProvider() {
            @Override
            public String name() {
                return "stub";
            }

            @Override
            public PaymentAuthorisationResponse authorise(PaymentAuthorisationRequest request) {
                return fn.apply(request);
            }
        };
    }
}
