package com.example.paymentadapter.provider.config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.payment.ProviderException.Reason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provider-agnostic JSON-over-HTTP client (JDK {@link HttpClient}). Bodies are plain maps/lists, so it
 * never knows any provider field. Handles timeouts, non-2xx statuses, unreadable JSON and logging.
 */
public class ProviderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpClient.class);

    public record Result(int statusCode, Object body) {
    }

    private final String providerName;
    private final String baseUrl;
    private final Duration readTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ProviderHttpClient(String providerName, String baseUrl, Duration connectTimeout, Duration readTimeout,
                              ObjectMapper objectMapper) {
        this.providerName = providerName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.readTimeout = readTimeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    public Result exchange(String method, String path, Object requestBody) {
        URI uri = URI.create(baseUrl + path);
        String json = requestBody == null ? null : toJson(requestBody);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(readTimeout)
                .header("Accept", "application/json");
        if (json == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json; charset=UTF-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        }

        log.info(">>> Outgoing HTTP {} {} body={}", method, uri, json == null ? "<none>" : json);
        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            log.warn("<<< HTTP {} {} timed out: {}", method, uri, e.getMessage());
            throw new ProviderException(Reason.TIMEOUT,
                    providerName + " did not respond to " + method + " " + path + " in time", null, e);
        } catch (IOException e) {
            log.warn("<<< HTTP {} {} failed: {}", method, uri, e.toString());
            throw new ProviderException(Reason.UNAVAILABLE,
                    providerName + " unreachable for " + method + " " + path + ": " + e, null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(Reason.UNAVAILABLE, "Interrupted while calling " + providerName, null, e);
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        log.info("<<< Incoming HTTP {} from {} {} ({} ms) body={}",
                response.statusCode(), method, uri, millis, response.body());

        int status = response.statusCode();
        if (status < 200 || status > 299) {
            throw new ProviderException(Reason.HTTP_ERROR,
                    providerName + " returned HTTP " + status + " for " + method + " " + path, status, null);
        }
        return new Result(status, parse(response.body(), method, path, status));
    }

    private Object parse(String body, String method, String path, int status) {
        if (body == null || body.isBlank()) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    providerName + " returned an empty body for " + method + " " + path, status, null);
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (JsonProcessingException e) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    providerName + " returned unparseable JSON for " + method + " " + path, status, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise provider request", e);
        }
    }
}
