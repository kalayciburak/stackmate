package io.github.kalayciburak.stackmate.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Additional bounded literal values that the mandatory sanitizer must redact. */
public class PrivacyProperties {

    private List<String> redactValues = new ArrayList<>();

    /** Creates privacy properties with no additional literal redaction values. */
    public PrivacyProperties() {}

    /**
     * Returns the additional literal values removed by the sanitizer.
     *
     * @return unmodifiable copy of configured literal redaction values
     */
    public List<String> getRedactValues() {
        return List.copyOf(redactValues);
    }

    /**
     * Replaces the additional literal values removed by the sanitizer.
     *
     * @param redactValues up to 32 nonblank values, each containing 3 through 256 characters
     */
    public void setRedactValues(List<String> redactValues) {
        Objects.requireNonNull(redactValues, "redactValues");
        List<String> validated =
                PropertyValidation.textList(redactValues, "privacy.redactValues", 32, 256);
        for (String value : validated) {
            if (value.codePointCount(0, value.length()) < 3) {
                throw new IllegalArgumentException(
                        "privacy.redactValues entries must contain at least three characters");
            }
        }
        this.redactValues = new ArrayList<>(validated);
    }
}
