package io.github.kalayciburak.stackmate.privacy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Removes secrets and unsafe terminal content before data crosses an output boundary. */
public interface Sanitizer {

    String sanitize(String value);

    String sanitize(String fieldName, String value);

    default Map<String, String> sanitize(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        Map<String, String> sanitized = new LinkedHashMap<>();
        values.forEach(
                (name, value) ->
                        sanitized.put(
                                Objects.requireNonNull(name, "field name"),
                                sanitize(name, Objects.requireNonNull(value, "field value"))));
        return Map.copyOf(sanitized);
    }
}
