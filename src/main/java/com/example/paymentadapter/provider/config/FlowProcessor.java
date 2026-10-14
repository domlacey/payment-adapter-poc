package com.example.paymentadapter.provider.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.payment.ProviderException.Reason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a {@link CompiledFlow} against a provider. Works on a JSON context:
 * <pre>
 * {
 *   "request":   { canonical request },
 *   "variables": { provider variables from configuration },
 *   "steps":     { "&lt;stepName&gt;": { "request": {...}, "status": 201, "response": {...} } }
 * }
 * </pre>
 * Every step: resolve path placeholders, build the body with the step's JOLT spec, call the provider,
 * record the exchange in the context, evaluate assertions. Finally the flow's response JOLT spec maps the
 * whole context to the canonical response.
 */
public class FlowProcessor {

    private static final Logger log = LoggerFactory.getLogger(FlowProcessor.class);

    private final ProviderHttpClient client;
    private final ObjectMapper objectMapper;

    public FlowProcessor(ProviderHttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public Object run(CompiledFlow flow, Object canonicalRequest, Map<String, Object> variables) {
        Map<String, Object> steps = new LinkedHashMap<>();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("request", canonicalRequest);
        context.put("variables", variables);
        context.put("steps", steps);

        for (CompiledFlow.Step step : flow.steps()) {
            FlowDefinition.Step def = step.definition();
            String path = JsonPaths.resolveTemplate(def.path(), context);
            Object body = step.request() == null ? null : step.request().transform(context);
            if (body != null) {
                log.info("Flow '{}' step '{}': JOLT mapped canonical request to provider request {}",
                        flow.name(), def.name(), toJson(body));
            }

            ProviderHttpClient.Result result = client.exchange(def.method(), path, body);

            Map<String, Object> exchange = new LinkedHashMap<>();
            exchange.put("request", body);
            exchange.put("status", result.statusCode());
            exchange.put("response", result.body());
            steps.put(def.name(), exchange);

            def.assertions().forEach(a -> check(flow, def, a, context));
        }

        Object canonical;
        try {
            canonical = flow.response().transform(context);
        } catch (RuntimeException e) {
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    "Response mapping in " + flow.source() + " failed: " + e.getMessage(), null, e);
        }
        log.info("Flow '{}': JOLT mapped provider response(s) to canonical response {}", flow.name(), toJson(canonical));
        return canonical;
    }

    private static void check(CompiledFlow flow, FlowDefinition.Step step, FlowDefinition.Assertion a,
                              Map<String, Object> context) {
        Object actual = JsonPaths.get(context, a.path());
        boolean ok = switch (a.rule()) {
            case "exists" -> actual != null;
            case "equals" -> actual != null && Objects.equals(String.valueOf(actual),
                    String.valueOf(JsonPaths.get(context, a.otherPath())));
            default -> throw new IllegalStateException("Unknown rule " + a.rule());
        };
        if (!ok) {
            String detail = a.message() != null ? a.message() : a.rule() + " failed for " + a.path();
            throw new ProviderException(Reason.MALFORMED_RESPONSE,
                    detail + " (flow '" + flow.name() + "', step '" + step.name() + "')");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
