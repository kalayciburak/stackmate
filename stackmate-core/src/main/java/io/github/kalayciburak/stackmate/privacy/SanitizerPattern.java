package io.github.kalayciburak.stackmate.privacy;

import java.util.Objects;
import java.util.regex.Pattern;

/** User-supplied bounded-input redaction rule. */
public record SanitizerPattern(Pattern pattern, String replacement) {

    public SanitizerPattern {
        pattern = Objects.requireNonNull(pattern, "pattern");
        replacement = SanitizerConfig.requireSafeReplacement(replacement, "replacement");
    }
}
