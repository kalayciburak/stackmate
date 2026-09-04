package io.github.kalayciburak.stackmate.privacy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * User-supplied bounded-input redaction rule.
 *
 * @param pattern regular expression matched against bounded sanitizer input
 * @param replacement safe text substituted for each match
 */
public record SanitizerPattern(Pattern pattern, String replacement) {

    /**
     * Validates and creates a custom redaction rule.
     *
     * @param pattern regular expression matched against bounded sanitizer input
     * @param replacement safe text substituted for each match
     */
    public SanitizerPattern {
        pattern = Objects.requireNonNull(pattern, "pattern");
        replacement = SanitizerConfig.requireSafeReplacement(replacement, "replacement");
    }
}
