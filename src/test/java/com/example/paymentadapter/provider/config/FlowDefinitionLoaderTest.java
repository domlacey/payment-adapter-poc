package com.example.paymentadapter.provider.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class FlowDefinitionLoaderTest {

    private final FlowDefinitionLoader loader = new FlowDefinitionLoader(new DefaultResourceLoader(), new ObjectMapper());

    @Test
    void loadsShippedJsonPlaceholderFlow() {
        CompiledFlow flow = loader.load("classpath:mappings/jsonplaceholder", "authorise");

        assertThat(flow.steps()).extracting(s -> s.definition().name())
                .containsExactly("merchantLookup", "createAuthorisation");
        assertThat(flow.steps().get(0).request()).isNull();
        assertThat(flow.steps().get(1).request()).isNotNull();
    }

    @Test
    void rejectsUnknownJoltOperation() {
        assertThatThrownBy(() -> loader.load("classpath:test-mappings/bad-jolt/", "authorise"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("bad-jolt/authorise.json");
    }

    @Test
    void rejectsMisspelledProperty() {
        assertThatThrownBy(() -> loader.load("classpath:test-mappings/typo/", "authorise"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("assertoins");
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> loader.load("classpath:test-mappings/nope/", "authorise"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not found");
    }
}
