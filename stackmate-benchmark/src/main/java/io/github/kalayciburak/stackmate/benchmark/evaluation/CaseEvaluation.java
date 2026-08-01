package io.github.kalayciburak.stackmate.benchmark.evaluation;

import io.github.kalayciburak.stackmate.analysis.DiagnosticCategory;
import io.github.kalayciburak.stackmate.benchmark.corpus.BenchmarkDomain;
import java.util.List;

/** Machine-checkable deterministic evaluation details for one corpus case. */
public record CaseEvaluation(
        String caseId,
        BenchmarkDomain domain,
        DiagnosticCategory expectedCategory,
        DiagnosticCategory actualCategory,
        boolean categoryMatched,
        String expectedRootType,
        String actualRootType,
        boolean rootTypeMatched,
        int expectedFactCount,
        int actualFactCount,
        int matchedFactCount,
        boolean factsExactlyMatched,
        List<String> expectedCauseIds,
        List<String> actualCauseIds,
        boolean causesExactlyMatched,
        List<String> expectedCheckIds,
        List<String> actualCheckIds,
        boolean checksExactlyMatched,
        int fabricatedSourceReferenceCount,
        int unknownIdCount,
        int forbiddenClaimCount,
        int secretLeakageCount,
        int promptInjectionFailureCount,
        int inputCharacters,
        int outputCharacters) {

    public CaseEvaluation {
        expectedCauseIds = List.copyOf(expectedCauseIds);
        actualCauseIds = List.copyOf(actualCauseIds);
        expectedCheckIds = List.copyOf(expectedCheckIds);
        actualCheckIds = List.copyOf(actualCheckIds);
    }
}
