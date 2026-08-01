package io.github.kalayciburak.stackmate.inference;

import java.util.List;
import java.util.Objects;

/** Parsed but untrusted explanation and supplied-ID selections produced by a model. */
public record StructuredInferenceOutput(
        String schemaVersion,
        InferenceLanguage language,
        String summary,
        String explanation,
        List<String> selectedCauseIds,
        List<String> selectedCheckIds,
        List<String> usedEvidenceIds,
        List<String> limitations) {

    public static final int MAXIMUM_TEXT_LENGTH = 8_192;

    public StructuredInferenceOutput {
        schemaVersion = Contracts.requireBoundedText(schemaVersion, "schemaVersion", 16);
        language = Objects.requireNonNull(language, "language");
        summary = Contracts.requireBoundedText(summary, "summary", 1_024);
        explanation = Contracts.requireBoundedText(explanation, "explanation", 4_096);
        selectedCauseIds =
                Contracts.immutableStableIds(
                        selectedCauseIds, "selectedCauseIds", InferenceRequest.MAXIMUM_CAUSES);
        selectedCheckIds =
                Contracts.immutableStableIds(
                        selectedCheckIds, "selectedCheckIds", InferenceRequest.MAXIMUM_CHECKS);
        usedEvidenceIds =
                Contracts.immutableStableIds(
                        usedEvidenceIds, "usedEvidenceIds", InferenceRequest.MAXIMUM_FACTS);
        limitations = immutableBoundedText(limitations, "limitations", 16, 512);
    }

    private static List<String> immutableBoundedText(
            List<String> values, String name, int maximumItems, int maximumText) {
        Objects.requireNonNull(values, name);
        if (values.size() > maximumItems) {
            throw new IllegalArgumentException(name + " exceeds limit " + maximumItems);
        }
        return values.stream()
                .map(value -> Contracts.requireBoundedText(value, name, maximumText))
                .toList();
    }
}
