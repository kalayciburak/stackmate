package io.github.kalayciburak.stackmate.benchmark.corpus.v2;

import java.util.Objects;

/** Analyzer-independent fact expectation for corpus V2. */
public record ExpectedFactV2(String type, String value) {
    public ExpectedFactV2 {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        if (type.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("expected fact type and value must not be blank");
        }
    }
}
