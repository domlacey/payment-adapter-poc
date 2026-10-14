package com.example.paymentadapter.provider.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;

import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.payment.ProviderException.Reason;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderHttpClientTest {

    private FakeProviderServer server;
    private ProviderHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeProviderServer();
        client = clientFor(server.baseUrl());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsJsonBodyAndParsesResponse() {
        server.stub("POST", "/posts", 201, "{\"a\":\"x\",\"id\":101}");

        ProviderHttpClient.Result result = client.exchange("POST", "/posts", Map.of("a", "x"));

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.body()).isEqualTo(Map.of("a", "x", "id", 101));
        assertThat(server.requests()).singleElement().satisfies(r -> {
            assertThat(r.method()).isEqualTo("POST");
            assertThat(r.body()).isEqualTo("{\"a\":\"x\"}");
        });
    }

    @Test
    void getSendsNoBody() {
        server.stub("GET", "/posts/1", 200, "{\"id\":1}");

        client.exchange("GET", "/posts/1", null);

        assertThat(server.requests().get(0).body()).isEmpty();
    }

    @Test
    void non2xxBecomesHttpError() {
        server.stub("POST", "/posts", 500, "{\"error\":\"boom\"}");

        assertThatThrownBy(() -> client.exchange("POST", "/posts", Map.of()))
                .isInstanceOfSatisfying(ProviderException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.HTTP_ERROR);
                    assertThat(e.httpStatus()).isEqualTo(500);
                });
    }

    @Test
    void invalidJsonBecomesMalformedResponse() {
        server.stub("GET", "/posts/1", 200, "<html>not json</html>");

        assertThatThrownBy(() -> client.exchange("GET", "/posts/1", null))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.MALFORMED_RESPONSE));
    }

    @Test
    void emptyBodyBecomesMalformedResponse() {
        server.stub("GET", "/posts/1", 200, "");

        assertThatThrownBy(() -> client.exchange("GET", "/posts/1", null))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.MALFORMED_RESPONSE));
    }

    @Test
    void slowResponseBecomesTimeout() {
        server.stub("GET", "/posts/1", 200, "{\"id\":1}", 2_000);

        assertThatThrownBy(() -> client.exchange("GET", "/posts/1", null))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.TIMEOUT));
    }

    @Test
    void connectionRefusedBecomesUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> clientFor("http://127.0.0.1:" + closedPort).exchange("GET", "/posts/1", null))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
    }

    private static ProviderHttpClient clientFor(String baseUrl) {
        return new ProviderHttpClient("test-provider", baseUrl, Duration.ofMillis(500), Duration.ofMillis(300),
                new ObjectMapper());
    }
}
