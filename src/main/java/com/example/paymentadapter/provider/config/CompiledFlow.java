package com.example.paymentadapter.provider.config;

import java.util.List;

import com.bazaarvoice.jolt.Chainr;

/** A validated flow file with its JOLT specs compiled. */
public record CompiledFlow(String name, String source, List<Step> steps, Chainr response) {

    /** {@code request} is null for steps without a body (e.g. GET). */
    public record Step(FlowDefinition.Step definition, Chainr request) {
    }
}
