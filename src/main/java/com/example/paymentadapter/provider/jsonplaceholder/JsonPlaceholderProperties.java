package com.example.paymentadapter.provider.jsonplaceholder;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payments.jsonplaceholder")
public record JsonPlaceholderProperties(
        String baseUrl,
        int merchantUserId,
        Duration connectTimeout,
        Duration readTimeout) {
}
