package com.example.paymentadapter.provider.jsonplaceholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.payment.ProviderException.Reason;
import com.example.paymentadapter.provider.jsonplaceholder.dto.CreatePostRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonPlaceholderHttpClientTest {

    private FakeProviderServer server;
    private JsonPlaceholderHttpClient client;

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
    void sendsJsonBodyAndParsesCreatedPost() {
        server.stub("POST", "/posts", 201,
                "{\"title\":\"t\",\"body\":\"b\",\"userId\":1,\"id\":101}");

        JsonPlaceholderHttpClient.Result result = client.createPost(new CreatePostRequest("t", "b", 1));

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.body().id()).isEqualTo(101);
        assertThat(server.requests()).singleElement().satisfies(r -> {
            assertThat(r.method()).isEqualTo("POST");
            assertThat(r.path()).isEqualTo("/posts");
            assertThat(r.body()).isEqualTo("{\"title\":\"t\",\"body\":\"b\",\"userId\":1}");
        });
    }

    @Test
    void ignoresUnknownProviderFields() {
        server.stub("GET", "/posts/1", 200, "{\"id\":1,\"userId\":1,\"title\":\"t\",\"body\":\"b\",\"extra\":true}");

        assertThat(client.getPost(1).body().userId()).isEqualTo(1);
    }

    @Test
    void non2xxBecomesHttpError() {
        server.stub("POST", "/posts", 500, "{\"error\":\"boom\"}");

        assertThatThrownBy(() -> client.createPost(new CreatePostRequest("t", "b", 1)))
                .isInstanceOfSatisfying(ProviderException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.HTTP_ERROR);
                    assertThat(e.httpStatus()).isEqualTo(500);
                });
    }

    @Test
    void notFoundBecomesHttpError() {
        server.stub("GET", "/posts/1", 404, "{}");

        assertThatThrownBy(() -> client.getPost(1))
                .isInstanceOfSatisfying(ProviderException.class, e -> assertThat(e.httpStatus()).isEqualTo(404));
    }

    @Test
    void invalidJsonBecomesMalformedResponse() {
        server.stub("GET", "/posts/1", 200, "<html>not json</html>");

        assertThatThrownBy(() -> client.getPost(1))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.MALFORMED_RESPONSE));
    }

    @Test
    void wrongJsonShapeBecomesMalformedResponse() {
        server.stub("GET", "/posts/1", 200, "[1,2,3]");

        assertThatThrownBy(() -> client.getPost(1))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.MALFORMED_RESPONSE));
    }

    @Test
    void emptyBodyBecomesMalformedResponse() {
        server.stub("GET", "/posts/1", 200, "");

        assertThatThrownBy(() -> client.getPost(1))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.MALFORMED_RESPONSE));
    }

    @Test
    void slowResponseBecomesTimeout() {
        server.stub("GET", "/posts/1", 200, "{\"id\":1}", 2_000);

        assertThatThrownBy(() -> client.getPost(1))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.TIMEOUT));
    }

    @Test
    void connectionRefusedBecomesUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        JsonPlaceholderHttpClient unreachable = clientFor("http://127.0.0.1:" + closedPort);

        assertThatThrownBy(() -> unreachable.getPost(1))
                .isInstanceOfSatisfying(ProviderException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
    }

    private static JsonPlaceholderHttpClient clientFor(String baseUrl) {
        JsonPlaceholderProperties props = new JsonPlaceholderProperties(
                baseUrl, 1, Duration.ofMillis(500), Duration.ofMillis(300));
        return new JsonPlaceholderHttpClient(props, new ObjectMapper());
    }
}
