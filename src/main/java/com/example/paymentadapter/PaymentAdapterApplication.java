package com.example.paymentadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentAdapterApplication.class, args);
    }
}
