package com.example.paymentadapter.provider.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonPathsTest {

    private final Map<String, Object> context = Map.of(
            "variables", Map.of("merchantUserId", 7, "name", "a b/c"),
            "steps", Map.of("s", Map.of("response", Map.of("items", List.of(Map.of("id", 42))))));

    @Test
    void readsNestedMapsAndListIndexes() {
        assertThat(JsonPaths.get(context, "variables.merchantUserId")).isEqualTo(7);
        assertThat(JsonPaths.get(context, "steps.s.response.items.0.id")).isEqualTo(42);
        assertThat(JsonPaths.get(context, "steps.s.response.items.5.id")).isNull();
        assertThat(JsonPaths.get(context, "steps.missing.response")).isNull();
    }

    @Test
    void resolvesAndEncodesPathTemplates() {
        assertThat(JsonPaths.resolveTemplate("/posts/{variables.merchantUserId}", context)).isEqualTo("/posts/7");
        assertThat(JsonPaths.resolveTemplate("/x/{variables.name}", context)).isEqualTo("/x/a+b%2Fc");
        assertThatThrownBy(() -> JsonPaths.resolveTemplate("/x/{variables.nope}", context))
                .hasMessageContaining("variables.nope");
    }
}
