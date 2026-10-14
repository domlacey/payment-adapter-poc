package com.example.paymentadapter.provider.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code payments.providers.<name>.*} - one entry per configuration-driven provider. */
@ConfigurationProperties(prefix = "payments")
public record ProvidersProperties(Map<String, ProviderSettings> providers) {

    public ProvidersProperties {
        providers = providers == null ? Map.of() : providers;
    }

    public record ProviderSettings(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            boolean simulation,
            String mappingLocation,
            Map<String, Object> variables) {

        public ProviderSettings {
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
            variables = variables == null ? Map.of() : variables;
        }
    }
}
