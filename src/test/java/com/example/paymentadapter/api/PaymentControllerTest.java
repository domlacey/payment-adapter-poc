package com.example.paymentadapter.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.paymentadapter.payment.PaymentAuthorisationResponse;
import com.example.paymentadapter.payment.PaymentService;
import com.example.paymentadapter.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final String VALID = "{\"paymentId\":\"PAY-1001\",\"amount\":1250,\"currency\":\"GBP\"}";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PaymentService paymentService;

    @Test
    void returns200ForSimulatedAcceptance() throws Exception {
        when(paymentService.authorise(any())).thenReturn(response(PaymentStatus.SIMULATED_ACCEPTED, "REF-1"));

        mvc.perform(post("/api/payments/authorise").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("PAY-1001"))
                .andExpect(jsonPath("$.providerReference").value("REF-1"))
                .andExpect(jsonPath("$.status").value("SIMULATED_ACCEPTED"))
                .andExpect(jsonPath("$.simulation").value(true));
    }

    @Test
    void returns504OnProviderTimeout() throws Exception {
        when(paymentService.authorise(any())).thenReturn(response(PaymentStatus.PROVIDER_TIMEOUT, null));

        mvc.perform(post("/api/payments/authorise").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value("PROVIDER_TIMEOUT"));
    }

    @Test
    void returns502OnInvalidProviderResponse() throws Exception {
        when(paymentService.authorise(any())).thenReturn(response(PaymentStatus.INVALID_PROVIDER_RESPONSE, null));

        mvc.perform(post("/api/payments/authorise").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadGateway());
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        mvc.perform(post("/api/payments/authorise").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"\",\"amount\":-5,\"currency\":\"gbp\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(3));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/payments/authorise").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    private static PaymentAuthorisationResponse response(PaymentStatus status, String ref) {
        return new PaymentAuthorisationResponse("PAY-1001", ref, status, "msg", "jsonplaceholder", true);
    }
}
