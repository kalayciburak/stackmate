package io.github.kalayciburak.stackmate.fingerprint;

import java.util.Objects;
import java.util.regex.Pattern;

/** A privacy-safe SHA-256 incident identifier. */
public record ExceptionFingerprint(String value) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ExceptionFingerprint {
        Objects.requireNonNull(value, "value");
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("value must be a lowercase SHA-256 digest");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
