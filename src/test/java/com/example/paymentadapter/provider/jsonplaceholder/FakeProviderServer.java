package com.example.paymentadapter.provider.jsonplaceholder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/** Minimal in-process HTTP server (JDK only) used to stand in for JSONPlaceholder in tests. */
public final class FakeProviderServer implements AutoCloseable {

    public record Recorded(String method, String path, String body) {
    }

    private record Stub(int status, String body, long delayMillis) {
    }

    private final HttpServer server;
    private final Map<String, Stub> stubs = new ConcurrentHashMap<>();
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();

    public FakeProviderServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
            Stub stub = stubs.getOrDefault(key, new Stub(404, "{}", 0));
            if (stub.delayMillis() > 0) {
                try {
                    Thread.sleep(stub.delayMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = stub.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stub.status(), bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            } catch (IOException ignored) {
                // client gave up (timeout tests)
            }
        });
        server.start();
    }

    public FakeProviderServer stub(String method, String path, int status, String body) {
        return stub(method, path, status, body, 0);
    }

    public FakeProviderServer stub(String method, String path, int status, String body, long delayMillis) {
        stubs.put(method + " " + path, new Stub(status, body, delayMillis));
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Recorded> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
