package com.example.paymentadapter.provider.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bazaarvoice.jolt.Chainr;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Reads and validates flow files at startup so a broken mapping fails fast, not on the first payment. */
@Component
public class FlowDefinitionLoader {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> RULES = Set.of("exists", "equals");

    private final ResourceLoader resourceLoader;
    private final ObjectMapper strictMapper;

    public FlowDefinitionLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.strictMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public CompiledFlow load(String mappingLocation, String flowName) {
        String location = (mappingLocation.endsWith("/") ? mappingLocation : mappingLocation + "/") + flowName + ".json";
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Mapping file not found: " + location);
        }
        try (InputStream in = resource.getInputStream()) {
            return compile(strictMapper.readValue(in, FlowDefinition.class), flowName, location);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Invalid mapping file " + location + ": " + e.getMessage(), e);
        }
    }

    private static CompiledFlow compile(FlowDefinition def, String expectedFlow, String location) {
        require(expectedFlow.equals(def.flow()), "'flow' must be '" + expectedFlow + "'");
        require(def.steps() != null && !def.steps().isEmpty(), "'steps' must not be empty");
        require(def.response() != null && !def.response().isEmpty(), "'response' JOLT spec is required");

        Set<String> names = new HashSet<>();
        List<CompiledFlow.Step> steps = def.steps().stream().map(step -> {
            require(step.name() != null && names.add(step.name()), "step names must be present and unique");
            require(step.method() != null && METHODS.contains(step.method()), "step '" + step.name() + "' has invalid method");
            require(step.path() != null && step.path().startsWith("/"), "step '" + step.name() + "' path must start with /");
            step.assertions().forEach(a -> {
                require(RULES.contains(a.rule()), "step '" + step.name() + "' has unknown assertion rule '" + a.rule() + "'");
                require(a.path() != null, "assertion in step '" + step.name() + "' needs 'path'");
                require(!"equals".equals(a.rule()) || a.otherPath() != null,
                        "'equals' assertion in step '" + step.name() + "' needs 'otherPath'");
            });
            Chainr request = step.request() == null ? null : Chainr.fromSpec(step.request());
            return new CompiledFlow.Step(step, request);
        }).toList();

        return new CompiledFlow(def.flow(), location, steps, Chainr.fromSpec(def.response()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
