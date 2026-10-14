package com.example.paymentadapter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import com.example.paymentadapter.provider.jsonplaceholder.FakeProviderServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Full Spring context; JSONPlaceholder replaced by a local fake so the test is offline and deterministic. */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowIntegrationTest {

    static final FakeProviderServer PROVIDER;

    static {
        try {
            PROVIDER = new FakeProviderServer()
                    .stub("GET", "/posts/1", 200, "{\"userId\":1,\"id\":1,\"title\":\"t\",\"body\":\"b\"}")
                    .stub("POST", "/posts", 201, "{\"title\":\"SIMULATED payment authorisation PAY-1001\","
                            + "\"body\":\"paymentId=PAY-1001;amountMinorUnits=1250;currency=GBP\",\"userId\":1,\"id\":101}");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("payments.jsonplaceholder.base-url", PROVIDER::baseUrl);
    }

    @AfterAll
    static void stop() {
        PROVIDER.close();
    }

    @Autowired
    MockMvc mvc;

    @Test
    void authoriseEndToEnd() throws Exception {
        mvc.perform(post("/api/payments/authorise").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"PAY-1001\",\"amount\":1250,\"currency\":\"GBP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("PAY-1001"))
                .andExpect(jsonPath("$.providerReference").value("JSONPLACEHOLDER-POST-101"))
                .andExpect(jsonPath("$.status").value("SIMULATED_ACCEPTED"))
                .andExpect(jsonPath("$.simulation").value(true));
    }
}
