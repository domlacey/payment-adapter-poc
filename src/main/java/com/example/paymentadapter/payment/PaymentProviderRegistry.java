package com.example.paymentadapter.payment;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** All provider adapters available to the core, keyed by {@link PaymentProvider#name()}. */
public final class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> byName;

    public PaymentProviderRegistry(Collection<? extends PaymentProvider> providers) {
        this.byName = providers.stream().collect(Collectors.toUnmodifiableMap(PaymentProvider::name, Function.identity()));
    }

    public Optional<PaymentProvider> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Set<String> names() {
        return byName.keySet();
    }
}
