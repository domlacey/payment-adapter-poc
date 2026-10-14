package com.example.paymentadapter.provider.jsonplaceholder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.payment.ProviderException.Reason;
import com.example.paymentadapter.provider.jsonplaceholder.dto.CreatePostRequest;
import com.example.paymentadapter.provider.jsonplaceholder.dto.PostResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thin HTTP client for JSONPlaceholder built on the JDK {@link HttpClient}. Responsible only for
 * transport concerns: serialisation, timeouts, status codes, logging and JSON parsing.
 */
@Component
public class JsonPlaceholderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(JsonPlaceholderHttpClient.class);

    public record Result(int statusCode, PostResponse body) {
    }

    private final JsonPlaceholderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public JsonPlaceholderHttpClient(JsonPlaceholderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public Result getPost(int id) {
        return send("GET", "/posts/" + id, null);
    }

    public Result createPost(CreatePostRequest request) {
        return send("POST", "/posts", toJson(request));
    }

    private Result send(String method, String path, String requestBody) {
        URI uri = URI.create(stripTrailingSlash(properties.baseUrl()) + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.readTimeout())
                .header("Accept", "application/json");
        if (requestBody == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json; charset=UTF-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(requestBody));
        }

        log.info(">>> Outgoing HTTP {} {} body={}", method, uri, requestBody == null ? "<none>" : requestBody);
        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            log.warn("<<< HTTP {} {} timed out: {}", method, uri, e.getMessage());
            throw new ProviderException(Reason.TIMEOUT,
                    "JSONPlaceholder did not respond to " + method + " " + path + " in time", null, e);
        } catch (IOException e) {
            log.warn("<<< HTTP {} {} failed: {}", method, uri, e.toString());
            throw new ProviderException(Reason.UNAVAILABLE,
                    "JSONPlaceholder unreachable for " + method + " " + path + ": " + e, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(Reason.UNAVAILABLE, "Interrupted while calling JSONPlaceholder", null, e);
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        log.info("<<< Incoming HTTP {} from {} {} ({} ms) body={}",
                response.statusCode(), method, uri, millis, response.body());

        int status = response.statusCode();
        if (status < 200 || status > 299) {
            throw new ProviderException(Reason.HTTP_ERROR,
                    "JSONPlaceholder returned HTTP " + status + " for " + method + " " + path, status, null);
        }
        return new Result(status, parse(response.body(), method, path, status));
    }

    private PostResponse parse(String body, String method, String path, int status) {
        if (body == null || body.isBlank()) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    "JSONPlaceholder returned an empty body for " + method + " " + path, status, null);
        }
        try {
            return objectMapper.readValue(body, PostResponse.class);
        } catch (JsonProcessingException e) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    "JSONPlaceholder returned unparseable JSON for " + method + " " + path, status, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise provider request", e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
