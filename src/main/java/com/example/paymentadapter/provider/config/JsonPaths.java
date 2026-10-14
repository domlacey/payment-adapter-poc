package com.example.paymentadapter.provider.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dot-path lookups ({@code steps.lookup.response.items.0.id}) over plain JSON maps and lists. */
final class JsonPaths {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)}");

    private JsonPaths() {
    }

    static Object get(Object root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list && part.matches("\\d+") && Integer.parseInt(part) < list.size()) {
                current = list.get(Integer.parseInt(part));
            } else {
                return null;
            }
        }
        return current;
    }

    /** Replaces {@code {dot.path}} placeholders with URL-encoded values from the context. */
    static String resolveTemplate(String template, Object context) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = get(context, m.group(1));
            if (value == null) {
                throw new IllegalStateException("Path placeholder {" + m.group(1) + "} has no value");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(
                    URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)));
        }
        return m.appendTail(out).toString();
    }
}
