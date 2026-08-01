package io.github.kalayciburak.stackmate.inference;

import java.util.List;
import java.util.Objects;

/** Parsed but untrusted model output that must pass {@link InferenceOutputValidator}. */
public record StructuredInferenceOutput(
        InferenceLanguage language,
        String summary,
        List<StructuredCause> causes,
        List<StructuredCheck> checks) {

    public static final int MAXIMUM_TEXT_LENGTH = 8_192;

    public StructuredInferenceOutput {
        language = Objects.requireNonNull(language, "language");
        summary = Contracts.requireBoundedText(summary, "summary", MAXIMUM_TEXT_LENGTH);
        causes = immutableBounded(causes, "causes", InferenceRequest.MAXIMUM_CAUSES);
        checks = immutableBounded(checks, "checks", InferenceRequest.MAXIMUM_CHECKS);
    }

    private static <T> List<T> immutableBounded(List<T> values, String name, int maximum) {
        Objects.requireNonNull(values, name);
        List<T> copy = List.copyOf(values);
        if (copy.size() > maximum) {
            throw new IllegalArgumentException(name + " exceeds limit " + maximum);
        }
        return copy;
    }
}
