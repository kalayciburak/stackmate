package io.github.kalayciburak.stackmate.inference;

import java.util.regex.Pattern;

/** A sanitized fact supplied to a local model. */
public record InferenceFact(String id, String type, String value) {

    private static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    public InferenceFact {
        id = Contracts.requireStableId(id, "id");
        type = Contracts.requireBoundedText(type, "type", 64);
        if (!TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("type must be a stable uppercase identifier");
        }
        value = Contracts.requireBoundedText(value, "value", 2_048);
    }
}
