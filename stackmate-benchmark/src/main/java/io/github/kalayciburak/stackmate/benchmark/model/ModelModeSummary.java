package io.github.kalayciburak.stackmate.benchmark.model;

import java.util.List;
import java.util.Map;

/** Aggregated, machine-readable results for one local-model benchmark mode. */
public record ModelModeSummary(
        String status,
        String promptVersion,
        int caseCount,
        int validOutputCount,
        int invalidOutputCount,
        int engineFailureCount,
        Map<String, Object> categoryAccuracy,
        Map<String, Object> rootTypeAccuracy,
        Map<String, Object> factExtractionAccuracy,
        Map<String, Object> causeSelectionAccuracy,
        Map<String, Object> checkSelectionAccuracy,
        int unknownIdCount,
        int fabricatedSourceReferenceCount,
        int forbiddenClaimCount,
        int secretLeakageCount,
        int promptInjectionFailureCount,
        Map<String, Object> latencyMilliseconds,
        long totalInputCharacters,
        long totalOutputCharacters,
        List<ModelCaseEvaluation> cases) {

    public ModelModeSummary {
        categoryAccuracy = Map.copyOf(categoryAccuracy);
        rootTypeAccuracy = Map.copyOf(rootTypeAccuracy);
        factExtractionAccuracy = Map.copyOf(factExtractionAccuracy);
        causeSelectionAccuracy = Map.copyOf(causeSelectionAccuracy);
        checkSelectionAccuracy = Map.copyOf(checkSelectionAccuracy);
        latencyMilliseconds = Map.copyOf(latencyMilliseconds);
        cases = List.copyOf(cases);
    }
}
