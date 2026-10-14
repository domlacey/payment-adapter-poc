package com.example.paymentadapter.provider.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import com.bazaarvoice.jolt.Chainr;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Data-driven mapping tests: every file in {@code src/test/resources/mapping-fixtures/} gives a context,
 * the mapping to apply ({@code target} = a step name or {@code response}) and the exact expected JSON.
 * Adding a fixture needs no Java.
 */
class MappingFixturesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final FlowDefinitionLoader loader = new FlowDefinitionLoader(new DefaultResourceLoader(), MAPPER);

    static Stream<Arguments> fixtures() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:mapping-fixtures/*.json");
        return Arrays.stream(files).map(f -> Arguments.of(f.getFilename(), f));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @SuppressWarnings("unchecked")
    void mappingProducesExpectedJson(String name, Resource file) throws Exception {
        Map<String, Object> fixture = MAPPER.readValue(file.getInputStream(), Map.class);
        CompiledFlow flow = loader.load((String) fixture.get("mappingLocation"), (String) fixture.get("flow"));
        String target = (String) fixture.get("target");

        Chainr spec = "response".equals(target) ? flow.response() : flow.steps().stream()
                .filter(s -> s.definition().name().equals(target)).findFirst()
                .orElseThrow(() -> new AssertionError("No step '" + target + "' in " + flow.source()))
                .request();
        Object actual = spec.transform(fixture.get("context"));

        JSONAssert.assertEquals(MAPPER.writeValueAsString(fixture.get("expected")),
                MAPPER.writeValueAsString(actual), JSONCompareMode.STRICT);
    }
}
