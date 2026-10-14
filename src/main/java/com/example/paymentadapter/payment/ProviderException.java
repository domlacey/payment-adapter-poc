package com.example.paymentadapter.payment;

public class ProviderException extends RuntimeException {

    public enum Reason { HTTP_ERROR, TIMEOUT, UNAVAILABLE, MALFORMED_RESPONSE }

    private final Reason reason;
    private final Integer httpStatus;

    public ProviderException(Reason reason, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.httpStatus = httpStatus;
    }

    public ProviderException(Reason reason, String message) {
        this(reason, message, null, null);
    }

    public Reason reason() {
        return reason;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}
