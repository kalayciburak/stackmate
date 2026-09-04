package io.github.kalayciburak.stackmate.evidence;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A single immutable fact that can be referenced by stable identifier.
 *
 * @param id stable identifier of the fact
 * @param type kind of fact represented by the value
 * @param source analyzer or observation that produced the fact
 * @param value sanitized fact value
 * @param sensitivity sensitivity classification of the value
 * @param reliability reliability assigned to the observation
 */
public record EvidenceItem(
        String id,
        EvidenceType type,
        String source,
        String value,
        EvidenceSensitivity sensitivity,
        EvidenceReliability reliability) {

    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    /**
     * Validates and creates an evidence item.
     *
     * @param id stable identifier of the fact
     * @param type kind of fact represented by the value
     * @param source analyzer or observation that produced the fact
     * @param value sanitized fact value
     * @param sensitivity sensitivity classification of the value
     * @param reliability reliability assigned to the observation
     */
    public EvidenceItem {
        id = requireStableId(id);
        type = Objects.requireNonNull(type, "type");
        source = requireText(source, "source");
        value = requireText(value, "value");
        sensitivity = Objects.requireNonNull(sensitivity, "sensitivity");
        reliability = Objects.requireNonNull(reliability, "reliability");
    }

    private static String requireStableId(String value) {
        Objects.requireNonNull(value, "id");
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("id must be a stable lowercase identifier");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
