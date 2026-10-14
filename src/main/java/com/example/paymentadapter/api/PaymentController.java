package com.example.paymentadapter.api;

import com.example.paymentadapter.payment.PaymentAuthorisationRequest;
import com.example.paymentadapter.payment.PaymentAuthorisationResponse;
import com.example.paymentadapter.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/authorise")
    public ResponseEntity<PaymentAuthorisationResponse> authorise(@Valid @RequestBody PaymentAuthorisationRequest request) {
        PaymentAuthorisationResponse response = paymentService.authorise(request);
        HttpStatus httpStatus = switch (response.status()) {
            case SIMULATED_ACCEPTED -> HttpStatus.OK;
            case PROVIDER_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case PROVIDER_ERROR, PROVIDER_UNAVAILABLE, INVALID_PROVIDER_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(httpStatus).body(response);
    }
}
