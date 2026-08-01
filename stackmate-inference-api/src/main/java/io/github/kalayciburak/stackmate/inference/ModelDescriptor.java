package io.github.kalayciburak.stackmate.inference;

import java.util.Objects;
import java.util.regex.Pattern;

/** Identifies a configured local model without exposing engine-specific types. */
public record ModelDescriptor(
        String id, String displayName, String version, ModelCapabilities capabilities) {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,127}");

    public ModelDescriptor {
        id = requireId(id);
        displayName = requireText(displayName, "displayName");
        version = requireText(version, "version");
        capabilities = Objects.requireNonNull(capabilities, "capabilities");
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        if (!ID.matcher(value).matches()) {
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
