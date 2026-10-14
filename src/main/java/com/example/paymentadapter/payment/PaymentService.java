package com.example.paymentadapter.payment;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Core payment service. Knows only the internal model and the {@link PaymentProvider} port.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProvider provider;
    private final ObjectMapper objectMapper;

    public PaymentService(List<PaymentProvider> providers,
                          @Value("${payments.provider}") String providerName,
                          ObjectMapper objectMapper) {
        Map<String, PaymentProvider> byName = providers.stream()
                .collect(Collectors.toMap(PaymentProvider::name, Function.identity()));
        this.provider = byName.get(providerName);
        if (this.provider == null) {
            throw new IllegalStateException("Unknown payments.provider '" + providerName
                    + "'. Available: " + byName.keySet());
        }
        this.objectMapper = objectMapper;
    }

    public PaymentAuthorisationResponse authorise(PaymentAuthorisationRequest request) {
        log.info("Internal request received: {}", toJson(request));
        PaymentAuthorisationResponse response;
        try {
            response = provider.authorise(request);
        } catch (ProviderException e) {
            log.warn("Provider '{}' failed: reason={} httpStatus={} message={}",
                    provider.name(), e.reason(), e.httpStatus(), e.getMessage());
            response = failure(request, e);
        }
        log.info("Final mapped internal response: {}", toJson(response));
        return response;
    }

    private PaymentAuthorisationResponse failure(PaymentAuthorisationRequest request, ProviderException e) {
        PaymentStatus status = switch (e.reason()) {
            case HTTP_ERROR -> PaymentStatus.PROVIDER_ERROR;
            case TIMEOUT -> PaymentStatus.PROVIDER_TIMEOUT;
            case UNAVAILABLE -> PaymentStatus.PROVIDER_UNAVAILABLE;
            case MALFORMED_RESPONSE -> PaymentStatus.INVALID_PROVIDER_RESPONSE;
        };
        String message = "SIMULATION FAILED - " + e.getMessage() + ". No payment was authorised.";
        return new PaymentAuthorisationResponse(request.paymentId(), null, status, message, provider.name(), true);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
