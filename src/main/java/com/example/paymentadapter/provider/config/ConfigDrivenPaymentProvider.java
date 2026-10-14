package com.example.paymentadapter.provider.config;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.paymentadapter.payment.PaymentAuthorisationRequest;
import com.example.paymentadapter.payment.PaymentAuthorisationResponse;
import com.example.paymentadapter.payment.PaymentProvider;
import com.example.paymentadapter.payment.ProviderException;
import com.example.paymentadapter.payment.ProviderException.Reason;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * A {@link PaymentProvider} whose behaviour is entirely defined by flow files. Converts the canonical
 * request to JSON, runs the flow, then binds and validates the result against the canonical model.
 * Platform guardrails ({@code provider}, {@code simulation}, matching {@code paymentId}) are enforced here,
 * not in the mapping.
 */
public class ConfigDrivenPaymentProvider implements PaymentProvider {

    static final String AUTHORISE_FLOW = "authorise";

    private final String name;
    private final ProvidersProperties.ProviderSettings settings;
    private final CompiledFlow authoriseFlow;
    private final FlowProcessor processor;
    private final ObjectMapper canonicalMapper;
    private final Validator validator;

    public ConfigDrivenPaymentProvider(String name, ProvidersProperties.ProviderSettings settings,
                                       FlowDefinitionLoader loader, ObjectMapper objectMapper, Validator validator) {
        this.name = name;
        this.settings = settings;
        this.authoriseFlow = loader.load(settings.mappingLocation(), AUTHORISE_FLOW);
        ProviderHttpClient client = new ProviderHttpClient(name, settings.baseUrl(),
                settings.connectTimeout(), settings.readTimeout(), objectMapper);
        this.processor = new FlowProcessor(client, objectMapper);
        this.canonicalMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentAuthorisationResponse authorise(PaymentAuthorisationRequest request) {
        Object canonicalRequest = canonicalMapper.convertValue(request, Map.class);
        Object mapped = processor.run(authoriseFlow, canonicalRequest, settings.variables());
        if (!(mapped instanceof Map<?, ?> map)) {
            throw invalid("response mapping must produce a JSON object");
        }
        Map<String, Object> canonical = (Map<String, Object>) map;
        canonical.put("provider", name);
        if (settings.simulation()) {
            canonical.put("simulation", true);
        }

        PaymentAuthorisationResponse response;
        try {
            response = canonicalMapper.convertValue(canonical, PaymentAuthorisationResponse.class);
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
        Set<ConstraintViolation<PaymentAuthorisationResponse>> violations = validator.validate(response);
        if (!violations.isEmpty()) {
            throw invalid(violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted().collect(Collectors.joining(", ")));
        }
        if (!request.paymentId().equals(response.paymentId())) {
            throw invalid("paymentId '" + response.paymentId() + "' does not match the request");
        }
        return response;
    }

    private ProviderException invalid(String detail) {
        return new ProviderException(Reason.MALFORMED_RESPONSE,
                "Mapped response from " + authoriseFlow.source() + " does not fit the canonical schema: " + detail);
    }
}
