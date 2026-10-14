package com.example.paymentadapter.provider.config;

import java.util.List;

/**
 * JSON shape of a flow file (e.g. {@code mappings/jsonplaceholder/authorise.json}).
 * {@code request} and {@code response} are JOLT chain specs.
 */
public record FlowDefinition(String flow, String description, List<Step> steps, List<Object> response) {

    public record Step(String name, String description, String method, String path,
                       List<Object> request, List<Assertion> assertions) {

        public Step {
            assertions = assertions == null ? List.of() : assertions;
        }
    }

    /** {@code rule} is {@code exists} or {@code equals}; paths are dot paths into the flow context. */
    public record Assertion(String rule, String path, String otherPath, String message) {
    }
}
