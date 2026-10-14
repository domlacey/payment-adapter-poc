package com.example.paymentadapter.provider.config;

import java.util.List;

import com.example.paymentadapter.payment.PaymentProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates one {@link ConfigDrivenPaymentProvider} per {@code payments.providers.<name>} entry. */
@Configuration
public class ConfigDrivenProviderConfiguration {

    @Bean
    PaymentProviderRegistry paymentProviderRegistry(ProvidersProperties properties, FlowDefinitionLoader loader,
                                                    ObjectMapper objectMapper, Validator validator) {
        List<ConfigDrivenPaymentProvider> providers = properties.providers().entrySet().stream()
                .map(e -> new ConfigDrivenPaymentProvider(e.getKey(), e.getValue(), loader, objectMapper, validator))
                .toList();
        return new PaymentProviderRegistry(providers);
    }
}
