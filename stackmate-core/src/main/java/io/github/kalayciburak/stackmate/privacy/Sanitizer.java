package io.github.kalayciburak.stackmate.privacy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Removes secrets and unsafe terminal content before data crosses an output boundary. */
public interface Sanitizer {

    /**
     * Bounds and redacts an unlabelled value.
     *
     * @param value value to sanitize, or {@code null}
     * @return safe bounded text; never {@code null}
     */
    String sanitize(String value);

    /**
     * Bounds and redacts a value, applying sensitive-field policy when the name matches.
     *
     * @param fieldName field name used for policy matching, or {@code null}
     * @param value value to sanitize, or {@code null}
     * @return safe bounded text; never {@code null}
     */
    String sanitize(String fieldName, String value);

    /**
     * Sanitizes each value in a map while preserving its field name.
     *
     * @param values field names and values to sanitize
     * @return an immutable map containing the sanitized values
     */
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
